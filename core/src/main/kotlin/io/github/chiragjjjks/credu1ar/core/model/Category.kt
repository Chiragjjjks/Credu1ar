package io.github.chiragjjjks.credu1ar.core.model

/**
 * Top-level groups. [slot] maps to the fixed categorical colour in docs/DESIGN.md (1..8);
 * 0 means neutral/no data colour. Slot order is a colour-blind-safety mechanism: never reorder.
 */
enum class Group(val id: String, val label: String, val slot: Int, val isSpend: Boolean) {
    FOOD("food", "Food & Drink", 1, true),
    GROCERIES("groceries", "Groceries", 2, true),
    TRANSPORT("transport", "Transport", 3, true),
    HOME_BILLS("home", "Home & Bills", 4, true),
    SHOPPING("shopping", "Shopping", 5, true),
    HEALTH("health", "Health", 6, true),
    FUN("fun", "Fun & Subscriptions", 7, true),
    PEOPLE_CASH("people", "People & Cash", 8, true),
    OTHER("other", "Other", 0, true),

    /** Money moving between your own pockets: never counted as spending. */
    MOVES("moves", "Money moves", 0, false),
    INCOME("income", "Money in", 0, false);

    companion object {
        /** Spending groups in the fixed chart order (colour slots 1..8, then Other). */
        val spendOrder: List<Group> = entries.filter { it.isSpend }.sortedBy { if (it.slot == 0) 99 else it.slot }
        fun byId(id: String): Group = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

enum class Category(val id: String, val label: String, val group: Group) {
    // Food & Drink
    FOOD_DELIVERY("food_delivery", "Food delivery", Group.FOOD),
    DINING_OUT("dining_out", "Dining out", Group.FOOD),
    CAFES("cafes", "Cafés & snacks", Group.FOOD),

    // Groceries: the split the owner cares about
    QUICK_COMMERCE("quick_commerce", "Quick commerce", Group.GROCERIES),
    PROVISION("provision", "Provision store", Group.GROCERIES),
    SUPERMARKET("supermarket", "Supermarket", Group.GROCERIES),
    FRESH_DAIRY("fresh_dairy", "Fresh & dairy", Group.GROCERIES),

    // Transport
    CAB_AUTO("cab_auto", "Cabs & autos", Group.TRANSPORT),
    FUEL("fuel", "Fuel", Group.TRANSPORT),
    METRO_BUS("metro_bus", "Metro & bus", Group.TRANSPORT),
    TRAVEL("travel", "Trains, flights & stays", Group.TRANSPORT),
    PARKING_TOLL("parking_toll", "Parking & tolls", Group.TRANSPORT),

    // Home & Bills
    RENT("rent", "Rent", Group.HOME_BILLS),
    UTILITIES("utilities", "Electricity, water & gas", Group.HOME_BILLS),
    MOBILE_INTERNET("mobile_internet", "Mobile & internet", Group.HOME_BILLS),
    EMI_LOANS("emi_loans", "EMI & loans", Group.HOME_BILLS),
    INSURANCE("insurance", "Insurance", Group.HOME_BILLS),

    // Shopping
    ONLINE_SHOPPING("online_shopping", "Online shopping", Group.SHOPPING),
    FASHION("fashion", "Fashion", Group.SHOPPING),
    ELECTRONICS("electronics", "Electronics", Group.SHOPPING),
    PERSONAL_CARE("personal_care", "Salon & personal care", Group.SHOPPING),

    // Health
    PHARMACY("pharmacy", "Pharmacy", Group.HEALTH),
    DOCTOR("doctor", "Doctors & hospitals", Group.HEALTH),
    FITNESS("fitness", "Fitness", Group.HEALTH),

    // Fun & Subscriptions
    STREAMING("streaming", "Streaming", Group.FUN),
    GAMES("games", "Games", Group.FUN),
    EVENTS("events", "Movies & events", Group.FUN),
    SUBSCRIPTIONS("subscriptions", "Apps & subscriptions", Group.FUN),

    // People & Cash
    SENT_TO_PEOPLE("sent_to_people", "Sent to people", Group.PEOPLE_CASH),
    ATM_CASH("atm_cash", "ATM cash", Group.PEOPLE_CASH),

    // Other
    EDUCATION("education", "Education", Group.OTHER),
    UNCATEGORISED("uncategorised", "Uncategorised", Group.OTHER),

    // Not spending
    CARD_BILL("card_bill", "Credit-card bill", Group.MOVES),
    SELF_TRANSFER("self_transfer", "Own-account transfer", Group.MOVES),
    INVESTMENTS("investments", "Investments", Group.MOVES),

    // Money in
    SALARY("salary", "Salary", Group.INCOME),
    REFUND("refund", "Refunds & cashback", Group.INCOME),
    MONEY_IN("money_in", "Money in", Group.INCOME);

    companion object {
        private val index = entries.associateBy { it.id }
        fun byId(id: String?): Category = index[id] ?: UNCATEGORISED
    }
}
