package dev.javarpg.server.docs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.server.config.DocsSwitch;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** FR-011: the documentation page is on for a loopback-only server, off otherwise, and always overridable. */
class DocsSwitchTest {

    private static MockEnvironment env(String address, String docs) {
        MockEnvironment env = new MockEnvironment();
        if (address != null) {
            env.setProperty("server.address", address);
        }
        if (docs != null) {
            env.setProperty("javarpg.docs.enabled", docs);
        }
        return env;
    }

    @Test
    void onByDefaultWhenTheServerListensOnLoopbackOnly() {
        assertTrue(DocsSwitch.docsEnabled(env("127.0.0.1", null)));
        assertTrue(DocsSwitch.docsEnabled(env("localhost", null)));
        assertTrue(DocsSwitch.docsEnabled(env("::1", null)));
        assertTrue(DocsSwitch.docsEnabled(env("[::1]", null)));
    }

    @Test
    void offByDefaultWhenTheServerIsReachableFromOtherMachines() {
        assertFalse(DocsSwitch.docsEnabled(env("0.0.0.0", null)));
        assertFalse(DocsSwitch.docsEnabled(env("192.168.1.20", null)));
        assertFalse(DocsSwitch.docsEnabled(env("game.example.org", null)));
        assertFalse(DocsSwitch.docsEnabled(env(null, null)), "no address means all interfaces");
        assertFalse(DocsSwitch.docsEnabled(env("", null)));
    }

    @Test
    void anExplicitSettingAlwaysWins() {
        assertTrue(DocsSwitch.docsEnabled(env("0.0.0.0", "true")));
        assertFalse(DocsSwitch.docsEnabled(env("127.0.0.1", "false")));
        assertTrue(DocsSwitch.docsEnabled(env(null, "TRUE")));
    }
}
