package com.relichunter.client;

import net.fabricmc.api.ClientModInitializer;

public class MiningDiagnostic implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        System.out.println("=================================");
        System.out.println("MINING DIAGNOSTIC LOADED");
        System.out.println("=================================");
    }
}