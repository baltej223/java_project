package com.example.chroner.call;

import com.example.chroner.domain.DomainException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Blocks tenant URLs that point at loopback, private networks, link-local
 * addresses (including cloud metadata), and non-80/443 ports.
 *
 * Java's built-in HttpClient has no connect-time hook, so this check runs
 * immediately before the request. See impl.doc for the residual DNS-rebinding
 * limitation.
 */
public class SsrfGuard {
    private static final byte[] NAT64_PREFIX = {
            0, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0
    };

    private final List<Cidr> allowList;

    public SsrfGuard(String allowCidrs) {
        this.allowList = new ArrayList<>();
        if (allowCidrs != null && !allowCidrs.isBlank()) {
            for (String item : allowCidrs.split(",")) {
                if (!item.isBlank()) {
                    allowList.add(Cidr.parse(item.trim()));
                }
            }
        }
    }

    public void check(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new DomainException("destination not allowed");
        }
        int port = uri.getPort();
        if (port == -1) {
            port = scheme.equalsIgnoreCase("https") ? 443 : 80;
        }
        if (port != 80 && port != 443) {
            throw new DomainException("destination not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new DomainException("destination not allowed");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new DomainException("destination not allowed");
        }
        for (InetAddress address : addresses) {
            if (!isAllowed(address)) {
                throw new DomainException("destination not allowed");
            }
        }
    }

    private boolean isAllowed(InetAddress address) {
        for (Cidr cidr : allowList) {
            if (cidr.contains(address)) {
                return true;
            }
        }
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isPublicIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return isPublicIpv6(address.getAddress());
        }
        return false;
    }

    private static boolean isPublicIpv4(byte[] raw) {
        int a = raw[0] & 0xFF;
        int b = raw[1] & 0xFF;
        int c = raw[2] & 0xFF;
        if (a == 0) {
            return false; // 0/8
        }
        if (a == 10) {
            return false; // 10/8
        }
        if (a == 100 && b >= 64 && b <= 127) {
            return false; // 100.64/10, CGNAT
        }
        if (a == 127) {
            return false; // loopback
        }
        if (a == 169 && b == 254) {
            return false; // link-local, metadata
        }
        if (a == 172 && b >= 16 && b <= 31) {
            return false; // 172.16/12
        }
        if (a == 192 && b == 0 && c == 0) {
            return false; // 192.0.0/24
        }
        if (a == 192 && b == 168) {
            return false; // 192.168/16
        }
        if (a == 198 && (b == 18 || b == 19)) {
            return false; // 198.18/15
        }
        if (a >= 224) {
            return false; // multicast and reserved
        }
        return true;
    }

    private static boolean isPublicIpv6(byte[] raw) {
        if (isIpv4Mapped(raw)) {
            byte[] v4 = {raw[12], raw[13], raw[14], raw[15]};
            return isPublicIpv4(v4);
        }
        // ::1 and ::
        boolean allZero = true;
        for (int i = 0; i < 15; i++) {
            if (raw[i] != 0) {
                allZero = false;
                break;
            }
        }
        if (allZero && (raw[15] == 0 || raw[15] == 1)) {
            return false;
        }
        int first = raw[0] & 0xFF;
        if ((first & 0xFE) == 0xFC) {
            return false; // fc00::/7, ULA
        }
        if (first == 0xFE && (raw[1] & 0xC0) == 0x80) {
            return false; // fe80::/10, link-local
        }
        if (first == 0xFF) {
            return false; // multicast
        }
        for (int i = 0; i < NAT64_PREFIX.length; i++) {
            if (raw[i] != NAT64_PREFIX[i]) {
                return true; // not NAT64
            }
        }
        return false; // 64:ff9b::/96
    }

    private static boolean isIpv4Mapped(byte[] raw) {
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return (raw[10] & 0xFF) == 0xFF && (raw[11] & 0xFF) == 0xFF;
    }
}
