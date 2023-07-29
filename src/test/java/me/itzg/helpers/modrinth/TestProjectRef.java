package me.itzg.helpers.modrinth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import me.itzg.helpers.modrinth.model.VersionType;

public class TestProjectRef {
    private String expectedSlug = "test_project1";
    private ProjectRef projectRefUT;


    @Test
    void testProjectRefHasProjectSlug() {
        projectRefUT = new ProjectRef(this.expectedSlug, null);

        assertEquals(expectedSlug, projectRefUT.getIdOrSlug());
    }

    @Test
    void testProjectRefHasVersionTypeWhenVersionIsType() {
        projectRefUT = new ProjectRef(this.expectedSlug, "release");
        assertEquals(VersionType.release, projectRefUT.getVersionType());
        assertFalse(projectRefUT.hasVersionId());
        assertFalse(projectRefUT.hasVersionName());

        projectRefUT = new ProjectRef(this.expectedSlug, "beta");
        assertEquals(VersionType.beta, projectRefUT.getVersionType());
        assertFalse(projectRefUT.hasVersionId());
        assertFalse(projectRefUT.hasVersionName());

        projectRefUT = new ProjectRef(this.expectedSlug, "alpha");
        assertEquals(VersionType.alpha, projectRefUT.getVersionType());
        assertFalse(projectRefUT.hasVersionId());
        assertFalse(projectRefUT.hasVersionName());
    }

    @Test
    void testProjectRefHasVersionIDWhenVersionIs8CharAlphaNum() {
        String expectedVersionId = "abcdEF12";
        projectRefUT = new ProjectRef(this.expectedSlug, expectedVersionId);
        assertEquals(expectedVersionId, projectRefUT.getVersionId());
        assertFalse(projectRefUT.hasVersionType());
        assertFalse(projectRefUT.hasVersionName());
    }

    @Test
    void testProjectRefHasVersionNameForOtherValues() {
        String expectedVersionName = "1.0.0";
        projectRefUT = new ProjectRef(this.expectedSlug, expectedVersionName);
        assertEquals(expectedVersionName, projectRefUT.getVersionName());
        assertFalse(projectRefUT.hasVersionId());
        assertFalse(projectRefUT.hasVersionType());
    }
}
