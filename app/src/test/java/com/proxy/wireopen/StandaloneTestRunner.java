package com.proxy.wireopen;

public class StandaloneTestRunner {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("    Starting Standalone Comprehensive Unit Tests  ");
        System.out.println("==================================================\n");

        PresetRegionTest.runAllTests();
        WireGuardConfigParserTest.runAllTests();
        OpenVpnConfigParserTest.runAllTests();
        
        System.out.println("--- Running TestAuthOverride ---");
        new TestAuthOverride().testAuthType();
        System.out.println("TestAuthOverride: ALL PASSED!\n");

        IpPureCheckerTest.runAllTests();

        System.out.println("==================================================");
        System.out.println("    ALL TESTS PASSED WITH 100% SUCCESS RATE!      ");
        System.out.println("==================================================");
    }
}
