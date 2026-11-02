#!/usr/bin/env python3
"""Turn a statement fixture (.txt) into a plain text PDF, one line of text per line, for emulator tests.

Fixtures are what Android's PdfRenderer text API returns for a real statement: pages separated by a line holding
a single form feed. This writes those lines back into a simple PDF (Helvetica, one text line per fixture line),
so PdfRenderer on the emulator should give the same text again and the whole import can be tested end to end.
Fictional fixtures only: never feed it a real statement.

Python 3 standard library only. Run isolated:
    python3 -I scripts/make-test-pdf.py core/src/test/resources/statements/<fixture>.txt out.pdf
    python3 -I scripts/make-test-pdf.py <fixture>.txt locked.pdf --password 1234   # RC4-128 (PDF 1.4 standard security)
    python3 -I scripts/make-test-pdf.py <fixture>.txt other.pdf --salt 2            # same text, different bytes (hash)
Output is deterministic: the same arguments give the same bytes.
"""
import argparse
import hashlib
import struct
import sys

PAGE_W, PAGE_H = 595, 842  # A4 in points
MARGIN, FONT_SIZE, LEADING = 36, 8, 11
PAD = bytes.fromhex("28BF4E5E4E758A4164004E56FFFA01082E2E00B6D0683E802F0CA9FE6453697A")
PERMISSIONS = -4  # everything allowed: the lock is only the open password, like a bank's statement PDF


def rc4(key: bytes, data: bytes) -> bytes:
    s = list(range(256))
    j = 0
    for i in range(256):
        j = (j + s[i] + key[i % len(key)]) % 256
        s[i], s[j] = s[j], s[i]
    out = bytearray()
    i = j = 0
    for b in data:
        i = (i + 1) % 256
        j = (j + s[i]) % 256
        s[i], s[j] = s[j], s[i]
        out.append(b ^ s[(s[i] + s[j]) % 256])
    return bytes(out)


def padded(pw: str) -> bytes:
    return (pw.encode("latin-1") + PAD)[:32]


class Security:
    """PDF 1.4 standard security handler, revision 3, 128-bit RC4 (ISO 32000-1, 7.6.3, algorithms 2, 3 and 5)."""

    def __init__(self, user_pw: str, file_id: bytes):
        key = hashlib.md5(padded(user_pw + "-owner")).digest()
        for _ in range(50):
            key = hashlib.md5(key).digest()
        o = rc4(key, padded(user_pw))
        for i in range(1, 20):
            o = rc4(bytes(k ^ i for k in key), o)
        self.o = o
        h = hashlib.md5(padded(user_pw) + o + struct.pack("<i", PERMISSIONS) + file_id).digest()
        for _ in range(50):
            h = hashlib.md5(h).digest()
        self.key = h[:16]
        u = rc4(self.key, hashlib.md5(PAD + file_id).digest())
        for i in range(1, 20):
            u = rc4(bytes(k ^ i for k in self.key), u)
        self.u = u + bytes(16)

    def encrypt(self, num: int, data: bytes) -> bytes:
        k = hashlib.md5(self.key + struct.pack("<i", num)[:3] + b"\x00\x00").digest()[:16]
        return rc4(k, data)

    def dict(self) -> bytes:
        return b"<< /Filter /Standard /V 2 /R 3 /Length 128 /O <%s> /U <%s> /P %d >>" % (
            self.o.hex().encode(), self.u.hex().encode(), PERMISSIONS)


def pdf_string(line: str) -> bytes:
    raw = line.encode("cp1252", errors="replace")  # WinAnsiEncoding; fixtures are ASCII
    return b"(" + raw.replace(b"\\", b"\\\\").replace(b"(", b"\\(").replace(b")", b"\\)") + b")"


def content(lines) -> bytes:
    out = [b"BT", b"/F1 %d Tf" % FONT_SIZE, b"%d TL" % LEADING, b"%d %d Td" % (MARGIN, PAGE_H - MARGIN - FONT_SIZE)]
    first = True
    for line in lines:
        if not first:
            out.append(b"T*")
        first = False
        if line.strip():
            out.append(pdf_string(line) + b" Tj")
    out.append(b"ET")
    return b"\n".join(out) + b"\n"


def build(pages, password, salt) -> bytes:
    file_id = hashlib.md5(("\f".join("\n".join(p) for p in pages) + "|" + str(salt)).encode()).digest()
    sec = Security(password, file_id) if password is not None else None
    objects = {}  # number -> bytes (body without "n 0 obj")
    n_pages = len(pages)
    first_page = 4
    kids = " ".join("%d 0 R" % (first_page + 2 * i) for i in range(n_pages))
    objects[1] = b"<< /Type /Catalog /Pages 2 0 R >>"
    objects[2] = b"<< /Type /Pages /Kids [ %s ] /Count %d >>" % (kids.encode(), n_pages)
    objects[3] = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
    for i, lines in enumerate(pages):
        page_num, content_num = first_page + 2 * i, first_page + 2 * i + 1
        objects[page_num] = (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %d %d] /Resources << /Font << /F1 3 0 R >> >> "
                             b"/Contents %d 0 R >>" % (PAGE_W, PAGE_H, content_num))
        data = content(lines)
        if sec:
            data = sec.encrypt(content_num, data)
        objects[content_num] = b"<< /Length %d >>\nstream\n" % len(data) + data + b"\nendstream"
    encrypt_num = None
    if sec:
        encrypt_num = first_page + 2 * n_pages
        objects[encrypt_num] = sec.dict()

    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    if salt is not None:
        out += b"%% salt %s\n" % str(salt).encode()
    offsets = {}
    for num in sorted(objects):
        offsets[num] = len(out)
        out += b"%d 0 obj\n" % num + objects[num] + b"\nendobj\n"
    xref = len(out)
    size = max(objects) + 1
    out += b"xref\n0 %d\n0000000000 65535 f \n" % size
    for num in range(1, size):
        out += b"%010d 00000 n \n" % offsets[num]
    trailer = b"<< /Size %d /Root 1 0 R /ID [<%s> <%s>]" % (size, file_id.hex().encode(), file_id.hex().encode())
    if encrypt_num:
        trailer += b" /Encrypt %d 0 R" % encrypt_num
    out += b"trailer\n" + trailer + b" >>\nstartxref\n%d\n%%%%EOF\n" % xref
    return bytes(out)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("fixture", help="statement fixture .txt (pages split by a form-feed line)")
    ap.add_argument("out", help="PDF to write")
    ap.add_argument("--password", help="lock the PDF with this open password (RC4-128)")
    ap.add_argument("--salt", help="change the file's bytes (and so its hash) without changing its text")
    a = ap.parse_args()
    with open(a.fixture, encoding="utf-8") as f:
        text = f.read()
    pages = [p.split("\n") for p in text.rstrip("\n").split("\n\f\n")]
    with open(a.out, "wb") as f:
        f.write(build(pages, a.password, a.salt))
    print("wrote %s: %d page(s)%s" % (a.out, len(pages), ", locked" if a.password is not None else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
