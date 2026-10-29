/* Credu1ar: local, deterministic illustrations. No requests, storage or telemetry. */
(() => {
  'use strict';
  const $ = (selector) => document.querySelector(selector);
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  const categories = [
    ['Food & Drink', '#3987E5'], ['Groceries', '#D95926'],
    ['Transport', '#199E70'], ['Home & Bills', '#C98500'],
    ['Shopping', '#D55181'], ['Health', '#008300'],
    ['Fun & Subscriptions', '#9085E9'], ['People & Cash', '#E66767']
  ];
  // All amounts and arithmetic are integer paise. These are fixtures, not a bank parser.
  const samples = [
    { merchant: 'Zepto', amount: 31200, category: 1, label: 'Groceries · Quick commerce', text: 'Rs.312.00 debited from A/c XX1234 on 29-10-26 to VPA zepto@axisbank.' },
    { merchant: 'Swiggy', amount: 48900, category: 0, label: 'Food & Drink · Food delivery', text: 'Rs.489.00 debited from A/c XX1234 on 29-10-26 to VPA swiggy@demobank.' },
    { merchant: 'Corner Provisions', amount: 84000, category: 1, label: 'Groceries · Provision store', text: 'Rs.840.00 debited from A/c XX1234 on 29-10-26 to VPA cornerprovisions@demobank.' },
    { merchant: 'Neighbourhood Market', amount: 126000, category: 1, label: 'Groceries · Supermarket', text: 'Rs.1260.00 debited from A/c XX1234 on 29-10-26 to VPA neighbourhoodmarket@demobank.' },
    { merchant: 'Salary', amount: 6000000, credit: true, label: 'Income · Salary', text: 'Rs.60000.00 credited to A/c XX1234 on 29-10-26. Salary from Example Studio.' },
    { merchant: 'Card-bill payment', amount: 1200000, settlement: true, label: 'Not spending · Card settlement', text: 'Rs.12000.00 debited from A/c XX1234 on 29-10-26 towards credit card bill payment. Card XX5678.' }
  ];
  const rupees = (paise) => '₹' + Math.trunc(paise / 100).toLocaleString('en-IN');
  const signed = (sample) => (sample.credit ? '↓ +' : '−') + rupees(sample.amount);
  const baseline = [120000, 110000, 70000, 0, 0, 0, 0, 0];
  let current = 0;
  let elapsed = 0;
  let demoRaf = 0;
  let lastTime = 0;
  let demoVisible = false;
  let playing = false;
  const demo = $('#demo');
  const text = $('#sms-text');
  const fields = $('.fields');
  const row = $('#demo-row');
  const note = $('#sample-note');

  function ringGradient(values) {
    const sum = values.reduce((a, b) => a + b, 0);
    let position = 0;
    const stops = [];
    // Fixed slot order; zero-value slots have no arc. Black separators remain visible.
    values.forEach((value, i) => {
      if (!value) return;
      const end = position + value * 100 / sum;
      const gap = Math.min(.6, (end - position) / 4);
      stops.push(`${categories[i][1]} ${position}% ${end - gap}%`, `#000 ${end - gap}% ${end}%`);
      position = end;
    });
    return `conic-gradient(${stops.join(',')})`;
  }

  function finishSample(announce = true) {
    playing = false;
    cancelAnimationFrame(demoRaf);
    demoRaf = 0;
    const sample = samples[current];
    text.textContent = sample.text;
    fields.style.visibility = '';
    row.style.visibility = '';
    $('#field-amount').textContent = signed(sample);
    $('#field-merchant').textContent = sample.merchant;
    $('#field-account').textContent = '••1234';
    $('#field-date').textContent = '29 Oct 2026';
    $('#row-merchant').textContent = sample.merchant;
    $('#row-category').textContent = sample.label;
    $('#row-amount').textContent = signed(sample);
    // Credit carries both direction and +, independently of colour.
    for (const element of [$('#field-amount'), $('#row-amount')]) {
      element.style.color = sample.credit ? '#68D98F' : '';
    }
    $('#demo-dot').style.background = sample.credit ? '#68D98F' : sample.settlement ? '#858585' : categories[sample.category][1];
    note.textContent = sample.credit ? 'Money in. Spending total stays unchanged.' : sample.settlement ? 'Not spending. Excluded to avoid counting card purchases twice.' : 'Included in spending.';
    const values = [...baseline];
    if (!sample.credit && !sample.settlement) values[sample.category] += sample.amount;
    const total = values.reduce((a, b) => a + b, 0);
    $('#demo-total').textContent = rupees(total);
    $('#demo-ring').style.background = ringGradient(values);
    $('#demo-ring').setAttribute('aria-label', `Sample spend ${rupees(total)}. ${values.slice(0, 3).map((v, i) => `${categories[i][0]} ${rupees(v)}`).join(', ')}.`);
    $('#demo-headline').textContent = sample.credit ? 'Salary is money in, not spending.' : sample.settlement ? 'A card settlement is not new spending.' : `This sample adds ${rupees(sample.amount)} to ${sample.category === 0 ? 'food & drink' : 'groceries'}.`;
    const bars = $('#demo-bars');
    bars.replaceChildren();
    values.slice(0, 3).forEach((value, i) => {
      const item = document.createElement('div');
      const label = document.createElement('div');
      label.className = 'demo-bar-label';
      const name = document.createElement('span');
      name.textContent = categories[i][0];
      const amount = document.createElement('strong');
      amount.textContent = rupees(value);
      label.append(name, amount);
      const meter = document.createElement('div');
      meter.className = 'meter';
      meter.setAttribute('aria-hidden', 'true');
      const fill = document.createElement('i');
      fill.style.setProperty('--bar', `${value * 100 / Math.max(...values)}%`);
      fill.style.setProperty('--category', categories[i][1]);
      meter.append(fill);
      item.append(label, meter);
      bars.append(item);
    });
    $('#trace').textContent = 'RECEIVED → PARSED → FILED';
    if (announce) $('#demo-announcement').textContent = `${sample.merchant}, ${signed(sample)}. ${sample.label}. ${note.textContent} Sample spend ${rupees(total)}.`;
  }

  function tickDemo(now) {
    demoRaf = 0;
    if (!playing || !demoVisible || document.hidden) { lastTime = 0; return; }
    if (lastTime) elapsed += Math.min(now - lastTime, 80);
    lastTime = now;
    const progress = Math.min(elapsed / 1100, 1);
    text.textContent = samples[current].text.slice(0, Math.ceil(samples[current].text.length * progress));
    if (progress === 1) finishSample();
    else demoRaf = requestAnimationFrame(tickDemo);
  }
  function resumeDemo() {
    lastTime = 0;
    if (playing && demoVisible && !document.hidden && !demoRaf) demoRaf = requestAnimationFrame(tickDemo);
  }
  function playSample() {
    cancelAnimationFrame(demoRaf);
    demoRaf = 0;
    elapsed = 0;
    lastTime = 0;
    $('#sample-index').textContent = `SAMPLE ${String(current + 1).padStart(2, '0')} / 06`;
    if (motion.matches) { finishSample(); return; }
    playing = true;
    text.textContent = '';
    fields.style.visibility = 'hidden';
    row.style.visibility = 'hidden';
    note.textContent = 'Reading the sample…';
    $('#trace').textContent = 'RECEIVING…';
    resumeDemo();
  }
  $('#next-sample').addEventListener('click', () => {
    current = (current + 1) % samples.length;
    playSample();
  });
  finishSample(false);
  let demoStarted = false;
  const visibilityObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      entry.target.classList.toggle('is-visible', entry.isIntersecting);
      entry.target.classList.toggle('section-paused', !entry.isIntersecting);
      if (entry.target === demo) {
        demoVisible = entry.isIntersecting;
        if (demoVisible && !demoStarted) { demoStarted = true; playSample(); }
        else if (demoVisible) resumeDemo();
        else { cancelAnimationFrame(demoRaf); demoRaf = 0; lastTime = 0; }
      }
    });
  }, { threshold: 0 });
  document.querySelectorAll('[data-motion]').forEach((element) => visibilityObserver.observe(element));

  // Progressive enhancement: content remains visible if scripts are unavailable.
  const reveals = [...document.querySelectorAll('[data-reveal]')];
  const revealObserver = new IntersectionObserver((entries) => entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add('revealed');
      revealObserver.unobserve(entry.target);
    }
  }), { threshold: .08 });
  if (!motion.matches) document.body.classList.add('motion-ready');
  reveals.forEach((element) => revealObserver.observe(element));

  const loader = $('#loader');
  let introRaf = 0;
  let introElapsed = 0;
  let introLast = 0;
  let introRunning = false;
  let exitTimer;
  const behindLoader = [...document.body.children].filter((el) => el !== loader && el.tagName !== 'SCRIPT');
  function closeIntro() {
    if (!introRunning) return;
    introRunning = false;
    cancelAnimationFrame(introRaf);
    const returnFocus = loader.contains(document.activeElement);
    behindLoader.forEach((el) => { el.inert = false; });
    loader.inert = true;
    loader.classList.add('leaving');
    // Remove immediately from the accessibility tree while the wipe finishes.
    loader.setAttribute('aria-hidden', 'true');
    if (returnFocus) $('.nav .wordmark').focus({ preventScroll: true });
    exitTimer = setTimeout(() => { loader.hidden = true; }, motion.matches ? 0 : 600);
  }
  function tickIntro(now) {
    introRaf = 0;
    if (!introRunning || document.hidden) { introLast = 0; return; }
    if (introLast) introElapsed += Math.min(now - introLast, 80);
    introLast = now;
    const progress = Math.min(1, introElapsed / 1100);
    $('.loader-number').textContent = String(Math.round(progress * 100)).padStart(3, '0');
    if (progress === 1) closeIntro();
    else introRaf = requestAnimationFrame(tickIntro);
  }
  $('#skip-intro').addEventListener('click', closeIntro);
  loader.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeIntro();
    if (event.key === 'Tab') { event.preventDefault(); $('#skip-intro').focus(); }
  });
  if (!motion.matches) {
    loader.hidden = false;
    introRunning = true;
    behindLoader.forEach((el) => { el.inert = true; });
    introRaf = requestAnimationFrame(tickIntro);
  }
  document.addEventListener('visibilitychange', () => {
    document.body.classList.toggle('tab-hidden', document.hidden);
    if (document.hidden) {
      cancelAnimationFrame(demoRaf); demoRaf = 0; lastTime = 0;
      cancelAnimationFrame(introRaf); introRaf = 0; introLast = 0;
    } else {
      resumeDemo();
      if (introRunning) introRaf = requestAnimationFrame(tickIntro);
    }
  });
  motion.addEventListener('change', () => {
    if (motion.matches) {
      closeIntro();
      clearTimeout(exitTimer);
      loader.hidden = true;
      document.body.classList.remove('motion-ready');
      if (playing) finishSample();
    }
  });
})();
