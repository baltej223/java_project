package com.example.crobat.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FairPickTest {
    @Test
    void givesEachTenantAnEqualShareFirst() {
        List<FairPick.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            candidates.add(new FairPick.Candidate("a" + i, "acme"));
            candidates.add(new FairPick.Candidate("b" + i, "beta"));
        }
        List<String> picked = FairPick.pick(candidates, 4);
        assertEquals(4, picked.size());
        long a = picked.stream().filter(id -> id.startsWith("a")).count();
        long b = picked.stream().filter(id -> id.startsWith("b")).count();
        assertEquals(2, a);
        assertEquals(2, b);
    }

    @Test
    void fillsLeftoverSlotsWhenOneTenantHasNoMoreWork() {
        List<FairPick.Candidate> candidates = new ArrayList<>();
        candidates.add(new FairPick.Candidate("a1", "acme"));
        candidates.add(new FairPick.Candidate("b1", "beta"));
        candidates.add(new FairPick.Candidate("b2", "beta"));
        candidates.add(new FairPick.Candidate("b3", "beta"));
        List<String> picked = FairPick.pick(candidates, 3);
        assertEquals(3, picked.size());
        assertEquals("a1", picked.get(0));
    }

    @Test
    void removesDuplicateIds() {
        List<FairPick.Candidate> candidates = List.of(
                new FairPick.Candidate("x", "acme"),
                new FairPick.Candidate("x", "acme"));
        assertEquals(List.of("x"), FairPick.pick(candidates, 5));
    }
}
