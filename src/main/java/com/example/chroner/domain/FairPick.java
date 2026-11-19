package com.example.chroner.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Distributes a claim batch fairly between tenants. */
public final class FairPick {
    private FairPick() {
    }

    public static class Candidate {
        private final String id;
        private final String tenantId;

        public Candidate(String id, String tenantId) {
            this.id = id;
            this.tenantId = tenantId;
        }

        public String getId() {
            return id;
        }

        public String getTenantId() {
            return tenantId;
        }
    }

    public static List<String> pick(List<Candidate> candidates, int n) {
        Map<String, Candidate> unique = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            unique.putIfAbsent(candidate.getId(), candidate);
        }
        List<Candidate> ordered = new ArrayList<>(unique.values());
        if (ordered.isEmpty() || n <= 0) {
            return new ArrayList<>();
        }

        Map<String, Integer> perTenant = new HashMap<>();
        Set<String> tenants = new HashSet<>();
        for (Candidate candidate : ordered) {
            tenants.add(candidate.getTenantId());
            perTenant.put(candidate.getTenantId(), 0);
        }

        int share = (n + tenants.size() - 1) / tenants.size();
        Set<String> picked = new HashSet<>();
        List<String> out = new ArrayList<>();

        for (int pass = 0; pass < 2 && out.size() < n; pass++) {
            for (Candidate candidate : ordered) {
                if (out.size() == n) {
                    break;
                }
                if (picked.contains(candidate.getId())) {
                    continue;
                }
                if (pass == 0 && perTenant.get(candidate.getTenantId()) >= share) {
                    continue;
                }
                picked.add(candidate.getId());
                perTenant.put(candidate.getTenantId(),
                        perTenant.get(candidate.getTenantId()) + 1);
                out.add(candidate.getId());
            }
        }
        return out;
    }
}
