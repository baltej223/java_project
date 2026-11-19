package com.example.chroner.call;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** A tiny IPv4/IPv6 CIDR block, used only for the development allowlist. */
public class Cidr {
    private final byte[] network;
    private final int prefixBits;

    private Cidr(byte[] network, int prefixBits) {
        this.network = network;
        this.prefixBits = prefixBits;
    }

    public static Cidr parse(String text) {
        String value = text.trim();
        int slash = value.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("CIDR needs a /prefix: " + text);
        }
        String addressText = value.substring(0, slash);
        int prefix = Integer.parseInt(value.substring(slash + 1));
        try {
            byte[] address = InetAddress.getByName(addressText).getAddress();
            if (prefix < 0 || prefix > address.length * 8) {
                throw new IllegalArgumentException("bad CIDR prefix: " + text);
            }
            return new Cidr(address, prefix);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("bad CIDR address: " + text);
        }
    }

    public boolean contains(InetAddress address) {
        byte[] candidate = address.getAddress();
        if (candidate.length != network.length) {
            return false;
        }
        int fullBytes = prefixBits / 8;
        int remainingBits = prefixBits % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (candidate[i] != network[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
    }
}
