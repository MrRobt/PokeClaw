// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// QA/debug builds must not show public GitHub release prompts during startup.

package io.agents.pokeclaw.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class UpdateCheckerTest {

    @Test
    public void debugBuildDisablesPublicUpdatePrompt() {
        assertFalse(UpdateChecker.isUpdateCheckEnabledForBuild());
    }
}
