package com.bond.mail.data.support

/** Public receiving addresses supplied by the project maintainer; never wallet credentials. */
internal data class SponsorshipWallet(val network: String, val address: String)

internal val sponsorshipWallets = listOf(
    SponsorshipWallet("Solana · SOL", "GseMb4yCgfhyMvkA7jP6QhMe4e5nMPnxgJqqrA8Aq7eJ"),
    SponsorshipWallet("Ethereum · ETH / ERC20", "0xcB2f6fc5eF905e89cDeE7F2eB59faD9A93043324"),
    SponsorshipWallet("TON", "UQATTF8wVv_Q8x42OYyDOUcM1Ti0HA-VdZ0b5zUd78_lEYqj"),
    SponsorshipWallet("TRON · TRX / TRC20", "TXDFyQKRSLt6dmbs3tcJbEJbcHsokbgKSn"),
    SponsorshipWallet("Sui · SUI", "0xd61db83d28fc0da34e55b9488d3927fc5b6515cc96c6109c0f8ed451980af4bb"),
    SponsorshipWallet("Bitcoin · BTC", "1BhMBUVLySJg3qNgFNxYPFMGbcd4KFxkrK"),
    SponsorshipWallet("Dogecoin · DOGE", "DEJ6MMqAjX55YfXXtFQVeYrCbadntYwZCs"),
)
