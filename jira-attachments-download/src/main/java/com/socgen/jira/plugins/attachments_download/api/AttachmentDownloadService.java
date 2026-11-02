package com.socgen.jira.plugins.attachments_download.api;

import com.atlassian.jira.user.ApplicationUser;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Service handling collection, filtering, permission checks, and zip streaming of Jira attachments.
 */
public interface AttachmentDownloadService {

    /**
     * Finds and collects all attachments matching the given filter criteria,
     * enforcing project and issue permissions for the specified user.
     *
     * @param criteria filter, grouping and sorting criteria
     * @param user the Jira user executing the request
     * @return list of exportable attachment descriptors with computed archive paths
     * @throws Exception if JQL execution or query parsing fails
     */
    List<ExportableAttachment> collectAttachments(AttachmentFilterCriteria criteria, ApplicationUser user) throws Exception;

    /**
     * Builds an export preview containing summary counts and size without packaging files.
     *
     * @param criteria filter criteria
     * @param user the Jira user executing the request
     * @return summary with total counts, total size, and item list
     * @throws Exception if query execution fails
     */
    AttachmentExportSummary preview(AttachmentFilterCriteria criteria, ApplicationUser user) throws Exception;

    /**
     * Collects matching attachments and streams them into a ZIP archive written directly
     * to the provided output stream.
     *
     * @param criteria filter and packaging criteria
     * @param user the Jira user executing the request
     * @param outputStream the stream to write the ZIP bytes into
     * @return summary metrics of the completed export
     * @throws IOException on I/O failure while reading attachments or writing to the stream
     */
    AttachmentExportSummary writeZipToStream(AttachmentFilterCriteria criteria, ApplicationUser user, OutputStream outputStream) throws IOException;
}
