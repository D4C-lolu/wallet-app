package com.interswitch.walletapp.models.enums;

public enum AccountType {
    SETTLEMENT,    // main account where transactions settle
    ESCROW,        // holds funds pending release
    WALLET,        // general purpose spending account
    VIRTUAL        // virtual account for receiving payments
}
