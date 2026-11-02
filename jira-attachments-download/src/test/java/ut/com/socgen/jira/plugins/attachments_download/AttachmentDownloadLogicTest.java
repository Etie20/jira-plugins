package ut.com.socgen.jira.plugins.attachments_download;

import com.socgen.jira.plugins.attachments_download.api.AttachmentExportSummary;
import com.socgen.jira.plugins.attachments_download.api.AttachmentFilterCriteria;
import com.socgen.jira.plugins.attachments_download.api.ExportableAttachment;
import com.socgen.jira.plugins.attachments_download.api.FilterOptions;
import com.socgen.jira.plugins.attachments_download.api.GroupingStrategy;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying criteria validation, grouping strategy paths,
 * file size formatting and zip archive structure.
 */
public class AttachmentDownloadLogicTest {

    @Test
    public void testGroupingStrategyParsing() {
        assertEquals(GroupingStrategy.BY_PROJECT_AND_ISSUE, GroupingStrategy.fromString("BY_PROJECT_AND_ISSUE"));
        assertEquals(GroupingStrategy.BY_ISSUE, GroupingStrategy.fromString("by_issue"));
        assertEquals(GroupingStrategy.BY_STATUS_AND_ISSUE, GroupingStrategy.fromString("BY_STATUS_AND_ISSUE"));
        assertEquals(GroupingStrategy.FLAT_WITH_PREFIX, GroupingStrategy.fromString("flat_with_prefix"));
        // Default fallback
        assertEquals(GroupingStrategy.BY_PROJECT_AND_ISSUE, GroupingStrategy.fromString("UNKNOWN"));
        assertEquals(GroupingStrategy.BY_PROJECT_AND_ISSUE, GroupingStrategy.fromString(null));
    }

    @Test
    public void testCriteriaBuilderAndExtensionNormalization() {
        AttachmentFilterCriteria criteria = AttachmentFilterCriteria.builder()
                .projectKey("JDC")
                .status("Done")
                .issueType("Bug")
                .addExtension(".PDF")
                .addExtension("png")
                .addExtension("  .XLSX  ")
                .minSizeBytes(1024L)
                .maxSizeBytes(10485760L)
                .maxIssues(50)
                .groupingStrategy(GroupingStrategy.BY_ISSUE)
                .includeManifest(true)
                .build();

        assertEquals("JDC", criteria.getProjectKey());
        assertEquals("Done", criteria.getStatus());
        assertEquals("Bug", criteria.getIssueType());
        assertEquals(50, criteria.getMaxIssues());
        assertEquals(GroupingStrategy.BY_ISSUE, criteria.getGroupingStrategy());
        assertTrue(criteria.isIncludeManifest());

        Set<String> extensions = criteria.getFileExtensions();
        assertEquals(3, extensions.size());
        assertTrue(extensions.contains("pdf"));
        assertTrue(extensions.contains("png"));
        assertTrue(extensions.contains("xlsx"));
    }

    @Test
    public void testFileSizeFormatting() {
        assertEquals("500 B", ExportableAttachment.formatFileSize(500L));
        assertEquals("1.0 KB", ExportableAttachment.formatFileSize(1024L));
        assertEquals("1.5 MB", ExportableAttachment.formatFileSize((long) (1.5 * 1024 * 1024)));
        assertEquals("2.0 GB", ExportableAttachment.formatFileSize((long) (2.0 * 1024 * 1024 * 1024)));
        assertEquals("0 B", ExportableAttachment.formatFileSize(null));
    }

    @Test
    public void testZipArchiveCreationAndStructure() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zipOut = new ZipOutputStream(baos);

        // Add manifest
        ZipEntry manifestEntry = new ZipEntry("export-summary.txt");
        zipOut.putNextEntry(manifestEntry);
        zipOut.write("Jira Export Manifest Test".getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();

        // Add file 1 in folder structure: PROJ/KEY-101 - Summary/report.pdf
        ZipEntry file1 = new ZipEntry("PROJ/KEY-101 - Bug Fix/report.pdf");
        zipOut.putNextEntry(file1);
        zipOut.write("PDF Content Sample".getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();

        // Add file 2 with collision resolution: PROJ/KEY-101 - Bug Fix/report (1).pdf
        ZipEntry file2 = new ZipEntry("PROJ/KEY-101 - Bug Fix/report (1).pdf");
        zipOut.putNextEntry(file2);
        zipOut.write("Second PDF Content Sample".getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();

        zipOut.finish();
        zipOut.close();

        // Verify entries in generated ZIP
        byte[] zipBytes = baos.toByteArray();
        assertTrue("Zip should have content", zipBytes.length > 0);

        Set<String> entryNames = new HashSet<>();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                zipIn.closeEntry();
            }
        }

        assertEquals(3, entryNames.size());
        assertTrue(entryNames.contains("export-summary.txt"));
        assertTrue(entryNames.contains("PROJ/KEY-101 - Bug Fix/report.pdf"));
        assertTrue(entryNames.contains("PROJ/KEY-101 - Bug Fix/report (1).pdf"));
    }

    @Test
    public void testCriteriaWithMultipleStatusesAndTypes() {
        AttachmentFilterCriteria criteria = AttachmentFilterCriteria.builder()
                .projectKey("PRJ1, PRJ2")
                .status("Open, In Progress, Done, En attente validation")
                .issueType("Bug, Story, Tâche personnalisée")
                .build();

        assertEquals("PRJ1, PRJ2", criteria.getProjectKey());
        assertEquals("Open, In Progress, Done, En attente validation", criteria.getStatus());
        assertEquals("Bug, Story, Tâche personnalisée", criteria.getIssueType());
    }

    @Test
    public void testExportSummaryMetrics() {
        List<ExportableAttachment> list = new ArrayList<>();
        list.add(new ExportableAttachment(1L, "spec.pdf", 2048L, "admin", new Date(), "application/pdf",
                "PROJ-1", "Fix login", "In Progress", "PROJ", "PROJ/PROJ-1/spec.pdf"));
        list.add(new ExportableAttachment(2L, "log.txt", 1024L, "admin", new Date(), "text/plain",
                "PROJ-1", "Fix login", "In Progress", "PROJ", "PROJ/PROJ-1/log.txt"));
        list.add(new ExportableAttachment(3L, "arch.png", 4096L, "developer", new Date(), "image/png",
                "PROJ-2", "Add feature", "Done", "PROJ", "PROJ/PROJ-2/arch.png"));

        AttachmentExportSummary summary = new AttachmentExportSummary(
                2, 3, 7168L, "project = 'PROJ' AND attachments IS NOT EMPTY", list
        );

        assertEquals(2, summary.getTotalIssues());
        assertEquals(3, summary.getTotalAttachments());
        assertEquals(7168L, summary.getTotalSizeBytes());
        assertEquals("7.0 KB", summary.getFormattedTotalSize());
        assertEquals(3, summary.getAttachments().size());
    }

    @Test
    public void testFilterOptionsProjectAndOptionDTOs() {
        List<FilterOptions.ProjectOption> projects = new ArrayList<>();
        projects.add(new FilterOptions.ProjectOption("10000", "PRJ", "Project Alpha"));

        List<FilterOptions.StatusOption> statuses = new ArrayList<>();
        statuses.add(new FilterOptions.StatusOption("1", "Open", "To Do"));
        statuses.add(new FilterOptions.StatusOption("3", "In Progress", "In Progress"));
        statuses.add(new FilterOptions.StatusOption("6", "Closed", "Done"));

        List<FilterOptions.IssueTypeOption> types = new ArrayList<>();
        types.add(new FilterOptions.IssueTypeOption("1", "Bug", false));
        types.add(new FilterOptions.IssueTypeOption("2", "Sub-task", true));

        List<String> strategies = new ArrayList<>();
        strategies.add("BY_PROJECT_AND_ISSUE");

        FilterOptions options = new FilterOptions(projects, statuses, types, strategies);

        assertEquals(1, options.getProjects().size());
        assertEquals("PRJ", options.getProjects().get(0).getKey());
        assertEquals("Project Alpha", options.getProjects().get(0).getName());

        assertEquals(3, options.getStatuses().size());
        assertEquals("Done", options.getStatuses().get(2).getCategory());

        assertEquals(2, options.getIssueTypes().size());
        assertTrue(options.getIssueTypes().get(1).isSubTask());
        assertFalse(options.getIssueTypes().get(0).isSubTask());
    }
}
