package com.proxy.wireopen.util;

import com.proxy.wireopen.model.PresetPortItem;

import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transport-aware latency probe tool for proxy endpoints.
 * Measures real socket RTT and ICMP reachability accurately.
 */
public class PortSpeedTester {

    public static final int DEFAULT_TIMEOUT_MS = 1500;
    private static final Pattern PING_TIME_PATTERN = Pattern.compile("time[=<]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*ms", Pattern.CASE_INSENSITIVE);

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(16, r -> {
        Thread t = new Thread(r, "PortSpeedTester-Worker");
        t.setDaemon(true);
        return t;
    });

    public interface SpeedTestListener {
        void onPortTested(PresetPortItem item, long latencyMs);
        void onAllCompleted(int successCount, int totalCount);
    }

    /**
     * Measures latency using the endpoint transport.
     *
     * Returns:
     *  >0 : RTT in milliseconds
     *  -2 : Timeout or unreachable
     */
    public static long measureLatency(String host, String ip, int port, int timeoutMs) {
        return measureLatency(host, ip, port, "TCP", timeoutMs);
    }

    public static long measureLatency(String host, String ip, int port, String transport, int timeoutMs) {
        String target = (ip != null && !ip.trim().isEmpty()) ? ip.trim() : host;
        if (target == null || target.isEmpty()) {
            return -2;
        }

        InetAddress resolvedAddress = null;
        try {
            resolvedAddress = InetAddress.getByName(target);
            target = resolvedAddress.getHostAddress();
        } catch (Exception ignored) {
            // Keep the original target for TCP fallback.
        }

        long reachabilityLatency = measureReachabilityLatency(
                resolvedAddress != null ? resolvedAddress : safeResolve(target), timeoutMs);
        if (reachabilityLatency > 0) {
            return reachabilityLatency;
        }

        if ("UDP".equalsIgnoreCase(transport)) {
            if (port == 53) {
                return measureDnsLatency(target, timeoutMs);
            }
            if (port == 123) {
                return measureNtpLatency(target, timeoutMs);
            }

            long fallback = measureTcpFallback(target, timeoutMs);
            return fallback > 0 ? fallback : -2;
        }

        return measureTcpLatency(target, port, timeoutMs);
    }

    private static InetAddress safeResolve(String target) {
        try {
            return InetAddress.getByName(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long measureReachabilityLatency(InetAddress address, int timeoutMs) {
        if (address == null) {
            return -2;
        }

        long start = System.currentTimeMillis();
        try {
            if (address.isReachable(Math.max(250, timeoutMs))) {
                return Math.max(1, System.currentTimeMillis() - start);
            }
        } catch (Exception ignored) {
        }

        Process process = null;
        try {
            String binary = address.getAddress().length == 16 ? "ping6" : "ping";
            process = new ProcessBuilder(
                    binary, "-c", "1", "-W", "1", address.getHostAddress()
            ).redirectErrorStream(true).start();

            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            if (!process.waitFor(Math.max(2, timeoutMs / 1000 + 1), TimeUnit.SECONDS)) {
                process.destroy();
                return -2;
            }

            Matcher matcher = PING_TIME_PATTERN.matcher(output);
            if (matcher.find()) {
                double ms = Double.parseDouble(matcher.group(1));
                return Math.max(1, Math.round(ms));
            }

            if (output.toString().contains("time<1")) {
                return 1;
            }
        } catch (Exception ignored) {
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return -2;
    }

    private static long measureTcpFallback(String target, int timeoutMs) {
        int[] commonPorts = {443, 80, 53};
        for (int commonPort : commonPorts) {
            long latency = measureTcpLatency(target, commonPort, timeoutMs);
            if (latency > 0) {
                return latency;
            }
        }
        return -2;
    }

    private static long measureTcpLatency(String target, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target, port), timeoutMs);
            return Math.max(1, System.currentTimeMillis() - start);
        } catch (ConnectException ce) {
            long rtt = Math.max(1, System.currentTimeMillis() - start);
            return rtt <= timeoutMs ? rtt : -2;
        } catch (SocketTimeoutException | UnknownHostException e) {
            return -2;
        } catch (Exception e) {
            return -2;
        }
    }

    private static long measureDnsLatency(String target, int timeoutMs) {
        byte[] query = new byte[] {
                0x12, 0x34, 0x01, 0x00,
                0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x07, 'e', 'x', 'a', 'm', 'p', 'l', 'e',
                0x03, 'c', 'o', 'm',
                0x00,
                0x00, 0x01,
                0x00, 0x01
        };
        byte[] response = new byte[512];
        long start = System.currentTimeMillis();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            DatagramPacket request = new DatagramPacket(
                    query, query.length, new InetSocketAddress(target, 53));
            socket.send(request);

            DatagramPacket reply = new DatagramPacket(response, response.length);
            socket.receive(reply);
            if (reply.getLength() < 12) {
                return -2;
            }
            return Math.max(1, System.currentTimeMillis() - start);
        } catch (IOException e) {
            return -2;
        }
    }

    private static long measureNtpLatency(String target, int timeoutMs) {
        byte[] request = new byte[48];
        request[0] = 0x1B;
        ByteBuffer.wrap(request, 40, 8).putLong(System.currentTimeMillis());

        byte[] response = new byte[128];
        long start = System.currentTimeMillis();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            DatagramPacket packet = new DatagramPacket(
                    request, request.length, new InetSocketAddress(target, 123));
            socket.send(packet);

            DatagramPacket reply = new DatagramPacket(response, response.length);
            socket.receive(reply);
            return reply.getLength() >= 48
                    ? Math.max(1, System.currentTimeMillis() - start)
                    : -2;
        } catch (IOException e) {
            return -2;
        }
    }

    /**
     * Asynchronously tests a single port item and triggers callback.
     * Duplicate tests for the same item are ignored.
     */
    public static void testSinglePort(PresetPortItem item, SpeedTestListener listener) {
        if (item == null) {
            return;
        }
        synchronized (item) {
            if (item.isTesting()) {
                return;
            }
            item.setTesting(true);
        }
        EXECUTOR.execute(() -> {
            try {
                long latency = measureLatency(
                        item.getHost(),
                        item.getIp(),
                        item.getPort(),
                        item.getTransport(),
                        DEFAULT_TIMEOUT_MS);
                item.setLatencyMs(latency);
                if (listener != null) {
                    listener.onPortTested(item, latency);
                }
            } finally {
                item.setTesting(false);
            }
        });
    }

    /**
     * Concurrently tests a list of port items across the thread pool.
     * Items that are already being tested are skipped.
     */
    public static void testAllPorts(List<PresetPortItem> ports, SpeedTestListener listener) {
        if (ports == null || ports.isEmpty()) {
            if (listener != null) listener.onAllCompleted(0, 0);
            return;
        }

        List<PresetPortItem> pending = new ArrayList<>();
        for (PresetPortItem item : ports) {
            if (item == null) continue;
            synchronized (item) {
                if (item.isTesting()) {
                    continue;
                }
                item.setTesting(true);
                pending.add(item);
            }
        }

        final int total = pending.size();
        if (total == 0) {
            if (listener != null) listener.onAllCompleted(0, 0);
            return;
        }

        final AtomicInteger completed = new AtomicInteger(0);
        final AtomicInteger success = new AtomicInteger(0);

        for (PresetPortItem item : pending) {
            EXECUTOR.execute(() -> {
                try {
                    long latency = measureLatency(
                            item.getHost(),
                            item.getIp(),
                            item.getPort(),
                            item.getTransport(),
                            DEFAULT_TIMEOUT_MS);
                    item.setLatencyMs(latency);
                    if (latency > 0) {
                        success.incrementAndGet();
                    }
                    if (listener != null) {
                        listener.onPortTested(item, latency);
                    }
                } finally {
                    item.setTesting(false);
                    if (completed.incrementAndGet() == total && listener != null) {
                        listener.onAllCompleted(success.get(), total);
                    }
                }
            });
        }
    }
}
