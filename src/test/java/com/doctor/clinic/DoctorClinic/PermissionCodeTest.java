package com.doctor.clinic.DoctorClinic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.doctor.clinic.DoctorClinic.security.PermissionCode;

class PermissionCodeTest {
    @Test
    void catalogueUsesUniqueCanonicalModuleActionCodes() {
        Set<String> codes = PermissionCode.allCodes();

        assertEquals(PermissionCode.values().length, codes.size());
        assertTrue(codes.stream().allMatch(code -> code.matches("[A-Z_]+:[A-Z]+")));
        assertEquals(codes, Arrays.stream(PermissionCode.values())
                .map(PermissionCode::code)
                .collect(Collectors.toSet()));
    }
}
