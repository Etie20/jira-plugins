package it.com.socgen.jira.plugins.attachments_download;

import org.junit.Test;
import org.junit.runner.RunWith;
import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;
import com.socgen.jira.plugins.attachments_download.api.AttachmentsDownloadPluginComponent;
import com.atlassian.sal.api.ApplicationProperties;

import static org.junit.Assert.assertEquals;

@RunWith(AtlassianPluginsTestRunner.class)
public class AttachmentsDownloadPluginComponentWiredTest {

    private final ApplicationProperties applicationProperties;
    private final AttachmentsDownloadPluginComponent pluginComponent;

    public AttachmentsDownloadPluginComponentWiredTest(ApplicationProperties applicationProperties,
                                                       AttachmentsDownloadPluginComponent pluginComponent) {
        this.applicationProperties = applicationProperties;
        this.pluginComponent = pluginComponent;
    }

    @Test
    public void testComponentName() {
        assertEquals("names do not match!",
                "attachmentsDownloadComponent:" + applicationProperties.getDisplayName(),
                pluginComponent.getName());
    }
}
