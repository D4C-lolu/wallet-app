package com.interswitch.walletapp.context;

public class IpContextHolder {

    private static final ThreadLocal<String> IP = new ThreadLocal<>();

    public static void set(String ip) { IP.set(ip); }
    public static String get() { return IP.get(); }
    public static void clear() { IP.remove(); }
}