package com.snothin.ghostsam.data.device

/** Device -> series table: GENERATED, do not edit. Coarse match only (firmware decisions stay
 *  payload-side); builds = display-only; kmi = uname guard input. */
internal object SeriesTable {

    internal data class Series(
        val id: String,
        val kmi: String,
        val preloadPath: String,
        val experimental: Boolean,
        val customLine: Boolean,
        val codenames: Set<String>,
        val models: Set<String>,
        val builds: Set<String>,
    )

    val ALL: List<Series> = listOf(
        Series(
            id = "s26",
            kmi = "android16-6.12",
            preloadPath = "/data/local/tmp/preload.so",
            experimental = false,
            customLine = true,
            codenames = setOf(
                "m1q", "m2q", "m3q", "m1s", "m2s",
            ),
            models = setOf(
                "SM-S9420", "SM-S942B", "SM-S942C", "SM-S942N", "SM-S942Q", "SM-S942U", "SM-S942U1",
                "SM-S942W", "SM-S942Z", "SM-S9470", "SM-S947B", "SM-S947C", "SM-S947N", "SM-S947Q",
                "SM-S947U", "SM-S947U1", "SM-S947W", "SM-S947Z", "SM-S9480", "SM-S948B", "SM-S948C",
                "SM-S948N", "SM-S948Q", "SM-S948U", "SM-S948U1", "SM-S948W", "SM-S948Z",
            ),
            builds = setOf(
                "S9420ZCS2AZE1", "S9420ZCS3AZF1", "S9420ZCS4AZH3", "S9420ZCS4AZI1", "S9420ZCU1AZCG",
                "S9420ZHS2AZE1", "S9420ZHS3AZF1", "S9420ZHS4AZH3", "S9420ZHU1AZCF", "S942BXXS1AZC7",
                "S942BXXS2AZE2", "S942BXXS3AZF1", "S942BXXS3AZF4", "S942BXXS4AZG5", "S942BXXS4AZHA",
                "S942BXXU1AZCF", "S942BXXU2AZDE", "S942NKSS1AZC7", "S942NKSS2AZE1", "S942NKSS3AZF1",
                "S942NKSS4AZG5", "S942NKSS4AZHA", "S942NKSU1AZCF", "S942NKSU2AZDE", "S942NKSU4BZI6",
                "S942QOPS1AZF2", "S942QOPS1AZH9", "S942QOPU1AZDE", "S942U1UES1AZC7", "S942U1UES2AZE1",
                "S942U1UES3AZF1", "S942U1UES4AZG3", "S942U1UES4AZH5", "S942U1UEU1AZCF", "S942U1UEU2AZDE",
                "S942USQS1AZC8", "S942USQS2AZE1", "S942USQS3AZF1", "S942USQS4AZG3", "S942USQS4AZH5",
                "S942USQU1AZCF", "S942USQU2AZDI", "S942USQU4BZID", "S942WVLS1AZC7", "S942WVLS2AZE1",
                "S942WVLS3AZF1", "S942WVLS4AZG3", "S942WVLS4AZH5", "S942WVLU1AZCF", "S942WVLU2AZDE",
                "S942ZSCS1AZF2", "S942ZSCS1AZH9", "S9470ZCS2AZE1", "S9470ZCS3AZF1", "S9470ZCS4AZH3",
                "S9470ZCS4AZI1", "S9470ZCU1AZCG", "S9470ZHS2AZE1", "S9470ZHS3AZF1", "S9470ZHS4AZH3",
                "S9470ZHU1AZCF", "S947BXXS1AZC7", "S947BXXS2AZE2", "S947BXXS3AZF1", "S947BXXS3AZF4",
                "S947BXXS4AZG5", "S947BXXS4AZHA", "S947BXXU1AZCF", "S947BXXU2AZDE", "S947NKSS1AZC7",
                "S947NKSS2AZE1", "S947NKSS3AZF1", "S947NKSS4AZG5", "S947NKSS4AZHA", "S947NKSU1AZCF",
                "S947NKSU2AZDE", "S947NKSU4BZI6", "S947QOPS1AZF2", "S947QOPS1AZH9", "S947QOPU1AZDE",
                "S947U1UES1AZC7", "S947U1UES2AZE1", "S947U1UES3AZF1", "S947U1UES4AZG3", "S947U1UES4AZH5",
                "S947U1UEU1AZCF", "S947U1UEU2AZDE", "S947USQS1AZC7", "S947USQS2AZE1", "S947USQS3AZF1",
                "S947USQS4AZG3", "S947USQS4AZH5", "S947USQU1AZCF", "S947USQU2AZDI", "S947USQU4BZID",
                "S947WVLS1AZC7", "S947WVLS2AZE1", "S947WVLS3AZF1", "S947WVLS4AZG3", "S947WVLS4AZH5",
                "S947WVLU1AZCF", "S947WVLU2AZDE", "S947ZSCS1AZF2", "S947ZSCS1AZH9", "S9480ZCS2AZE1",
                "S9480ZCS3AZF1", "S9480ZCS4AZG1", "S9480ZCS4AZHL", "S9480ZCS4AZI1", "S9480ZCU1AZCG",
                "S9480ZHS2AZE1", "S9480ZHS3AZF1", "S9480ZHS4AZHL", "S9480ZHU1AZCF", "S948BXXS1AZC7",
                "S948BXXS2AZE2", "S948BXXS2AZE3", "S948BXXS3AZF1", "S948BXXS3AZF4", "S948BXXS4AZG6",
                "S948BXXS4AZHL", "S948BXXS4AZHM", "S948BXXU1AZCF", "S948CONS1AZF2", "S948CONS1AZHL",
                "S948NKSS1AZC7", "S948NKSS2AZE1", "S948NKSS3AZF1", "S948NKSS4AZG3", "S948NKSU1AZCF",
                "S948NKSU2AZDE", "S948NKSU4AZHJ", "S948NKSU4BZI6", "S948QOPS1AZF2", "S948QOPS1AZHL",
                "S948QOPU1AZDE", "S948U1UES1AZC7", "S948U1UES2AZE1", "S948U1UES3AZF1", "S948U1UES4AZG3",
                "S948U1UES4AZHL", "S948U1UEU1AZCF", "S948U1UEU2AZDE", "S948USQS2AZE1", "S948USQS3AZF1",
                "S948USQS4AZG3", "S948USQS4AZHL", "S948USQU1AZCF", "S948USQU2AZDI", "S948USQU4BZID",
                "S948WVLS1AZC7", "S948WVLS2AZE1", "S948WVLS3AZF1", "S948WVLS4AZG3", "S948WVLS4AZHL",
                "S948WVLU1AZCF", "S948WVLU2AZDE", "S948ZSCS1AZF2", "S948ZSCS1AZHL",
            ),
        ),
        Series(
            id = "zf8",
            kmi = "android16-6.12",
            preloadPath = "/data/local/tmp/preload.so",
            experimental = true,
            customLine = true,
            codenames = setOf(
                "q8q", "h8q",
            ),
            models = setOf(
                "SM-F9710", "SM-F971B", "SM-F971C", "SM-F971N", "SM-F971Q", "SM-F971U", "SM-F971U1",
                "SM-F971W", "SM-F971Z", "SM-F9760", "SM-F976B", "SM-F976C", "SM-F976N", "SM-F976Q",
                "SM-F976U", "SM-F976U1", "SM-F976W", "SM-F976Z",
            ),
            builds = setOf(
                "F9710ZCS2AZH8", "F9710ZCU1AZFW", "F9710ZCU1AZGI", "F9710ZCU3AZI9", "F9710ZSS2AZH7",
                "F9710ZSU1AZFX", "F9710ZSU1AZGI", "F9710ZSU3AZI5", "F971BXXS2AZH7", "F971BXXU1AZFW",
                "F971BXXU1AZGI", "F971BXXU3AZI5", "F971NKSS2AZH7", "F971NKSU1AZFW", "F971NKSU1AZGI",
                "F971NKSU3AZI5", "F971QOPS1AZH7", "F971QOPU1AZFW", "F971QOPU1AZGI", "F971U1UES2AZH7",
                "F971U1UEU1AZFW", "F971U1UEU1AZGI", "F971U1UEU3AZI5", "F971U1UEU3AZI8", "F971USQS2AZH7",
                "F971USQS2AZH8", "F971USQU1AZFW", "F971USQU1AZGI", "F971USQU3AZI5", "F971USQU3AZI8",
                "F971WVLS2AZH7", "F971WVLU1AZFW", "F971WVLU1AZGJ", "F971WVLU3AZI5", "F971ZSCS1AZH7",
                "F971ZSCU1AZFW", "F971ZSCU1AZGI", "F9760ZCS2AZH8", "F9760ZCU1AZFW", "F9760ZCU1AZGI",
                "F9760ZCU3AZI9", "F9760ZSS2AZH7", "F9760ZSU1AZFX", "F9760ZSU1AZGI", "F9760ZSU3AZI5",
                "F976BXXS2AZH7", "F976BXXU1AZFW", "F976BXXU1AZGI", "F976BXXU3AZI5", "F976NKSS2AZH7",
                "F976NKSU1AZFW", "F976NKSU1AZGI", "F976NKSU3AZI5", "F976QOPS1AZH7", "F976QOPU1AZFW",
                "F976QOPU1AZGI", "F976U1UES2AZH7", "F976U1UEU1AZFW", "F976U1UEU1AZGI", "F976U1UEU3AZI5",
                "F976U1UEU3AZI8", "F976USQS2AZH7", "F976USQU1AZFW", "F976USQU1AZGI", "F976USQU3AZI5",
                "F976USQU3AZI8", "F976WVLS2AZH7", "F976WVLU1AZFW", "F976WVLU1AZGI", "F976WVLU3AZI5",
                "F976ZSCS1AZH7", "F976ZSCU1AZFW", "F976ZSCU1AZGI",
            ),
        ),
        Series(
            id = "s22",
            kmi = "android12-5.10",
            preloadPath = "/data/local/tmp/preload.so",
            experimental = false,
            customLine = true,
            codenames = setOf(
                "b0q", "r0q", "g0q", "r0s", "g0s", "b0s",
            ),
            models = setOf(
                "SM-S9010", "SM-S901B", "SM-S901E", "SM-S901N", "SM-S901U", "SM-S901U1", "SM-S901W",
                "SM-S9060", "SM-S906B", "SM-S906E", "SM-S906N", "SM-S906U", "SM-S906U1", "SM-S906W",
                "SM-S9080", "SM-S908B", "SM-S908E", "SM-S908N", "SM-S908U", "SM-S908U1", "SM-S908W",
            ),
            builds = setOf(
                "S9010ZCSAGZB4", "S9010ZCSBGZH3", "S9010ZHSAGZB4", "S9010ZHSBGZH3", "S901BXXSNGZD7",
                "S901BXXSOGZH3", "S901EXXSDGZB6", "S901EXXSEGZE3", "S901EXXSEGZF1", "S901EXXSEGZH4",
                "S901NKSS8GZB2", "S901NKSS9GZE5", "S901NKSS9GZH3", "S901U1UES9GZB4", "S901U1UESAGZF3",
                "S901U1UESAGZH3", "S901USQS9GZB4", "S901USQSAGZF3", "S901USQSAGZH3", "S901WVLS9GZB6",
                "S901WVLSAGZE3", "S901WVLSAGZH3", "S9060ZCSAGZB4", "S9060ZCSBGZH3", "S9060ZHSAGZB4",
                "S9060ZHSBGZH3", "S906BXXSNGZD7", "S906BXXSOGZH3", "S906EXXSDGZB6", "S906EXXSEGZF1",
                "S906EXXSEGZH4", "S906NKSS8GZB2", "S906NKSS9GZE5", "S906NKSS9GZH3", "S906U1UES9GZB4",
                "S906U1UESAGZF3", "S906U1UESAGZH3", "S906USQS9GZB4", "S906USQSAGZF3", "S906USQSAGZH3",
                "S906WVLS9GZB6", "S906WVLSAGZE3", "S906WVLSAGZH3", "S9080ZCSAGZB4", "S9080ZCSBGZE3",
                "S9080ZCSBGZH3", "S9080ZHSAGZB4", "S9080ZHSBGZH3", "S908BXXSNGZD7", "S908BXXSOGZH3",
                "S908EXXSDGZB6", "S908EXXSEGZE3", "S908EXXSEGZF1", "S908EXXSEGZH4", "S908NKSS8GZB2",
                "S908NKSS9GZE5", "S908NKSS9GZH3", "S908U1UES9GZB4", "S908U1UESAGZF3", "S908U1UESAGZH3",
                "S908USQS9GZB4", "S908USQSAGZF3", "S908USQSAGZH3", "S908WVLS9GZB6", "S908WVLSAGZE3",
                "S908WVLSAGZH3",
            ),
        ),
    )

    val BY_CODENAME: Map<String, Series> = buildMap {
        ALL.forEach { s -> s.codenames.forEach { c -> putIfAbsent(c, s) } }
    }

    val BY_MODEL: Map<String, Series> = buildMap {
        ALL.forEach { s -> s.models.forEach { m -> putIfAbsent(m, s) } }
    }
}
