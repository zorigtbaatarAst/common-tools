package mn.astvision.commontools.authutil;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * @author zorigtbaatar
 */

public class IpUtils {
    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^([0-9]{1,3}\\.){3}[0-9]{1,3}$");

    private static final Pattern IPV6_PATTERN =
            Pattern.compile("([0-9a-fA-F]{0,4}:){1,7}[0-9a-fA-F]{0,4}");

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA"
    };

    /**
     * Extracts the client IP address from request headers or remote address.
     */
    public static String getClientIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                String ip = value.split(",")[0].trim();
                if (isValidIp(ip)) {
                    return normalizeIp(ip);
                }
            }
        }

        String remoteIp = request.getRemoteAddr();
        return normalizeIp(remoteIp);
    }

    /**
     * Checks whether the given string is a valid IPv4 or IPv6 address.
     */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        try {
            InetAddress byName = InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Checks whether the given IP is a valid IPv4 address.
     */
    public static boolean isValidIPv4(String ip) {
        return ip != null && IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * Checks whether the given IP is a valid IPv6 address.
     */
    public static boolean isValidIPv6(String ip) {
        return ip != null && IPV6_PATTERN.matcher(ip).matches();
    }

    /**
     * Normalizes the loopback IPv6 (::1 or 0:0:0:0:0:0:0:1) to 127.0.0.1
     */
    public static String normalizeIp(String ip) {
        if (ip == null) return null;
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }

    /**
     * Checks if the given IP address is in a private (RFC1918) range.
     */
    public static boolean isPrivateIp(String ip) {
        if (!isValidIp(ip)) return false;
        return ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")
                || "127.0.0.1".equals(ip);
    }

    /**
     * Checks if two IPs are the same after normalization.
     */
    public static boolean equalsNormalized(String ip1, String ip2) {
        return normalizeIp(ip1).equals(normalizeIp(ip2));
    }


}
