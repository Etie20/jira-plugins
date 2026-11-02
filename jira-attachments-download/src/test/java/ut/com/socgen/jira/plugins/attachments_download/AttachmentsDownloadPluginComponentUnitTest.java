package ut.com.socgen.jira.plugins.attachments_download;

import com.socgen.jira.plugins.attachments_download.api.AttachmentsDownloadPluginComponent;
import com.socgen.jira.plugins.attachments_download.impl.AttachmentsDownloadPluginComponentImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AttachmentsDownloadPluginComponentUnitTest {

    @Test
    public void testComponentName() {
        AttachmentsDownloadPluginComponent component = new AttachmentsDownloadPluginComponentImpl(null);
        assertEquals("names do not match!", "attachmentsDownloadComponent", component.getName());
    }
}
