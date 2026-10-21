package com.moodtools.hub;

public class RootCommandFixture {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("timeout")) { Thread.sleep(10000); return; }
        for (int i = 0; i < 300000; i++) System.out.print('x');
        System.out.flush();
        System.exit(Integer.parseInt(args[0]));
    }
}
