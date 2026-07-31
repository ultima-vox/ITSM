package ru.ultimavox.itsm.platform.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LocalMetadataAttachmentStorageTest {

  @Test
  void stores_metadata_only_and_finds_by_key() {
    LocalMetadataAttachmentStorage storage = new LocalMetadataAttachmentStorage();
    byte[] bytes = "file-bytes".getBytes(StandardCharsets.UTF_8);

    AttachmentStorage.StoredAttachment stored = storage.store(new AttachmentStorage.StoreRequest(
        "tickets/1/note.pdf",
        "application/pdf",
        bytes.length,
        "note.pdf",
        new ByteArrayInputStream(bytes)
    ));

    assertThat(stored.backend()).isEqualTo(LocalMetadataAttachmentStorage.BACKEND);
    assertThat(stored.objectKey()).isEqualTo("tickets/1/note.pdf");
    assertThat(stored.sizeBytes()).isEqualTo(bytes.length);
    assertThat(stored.storageUri()).startsWith("local://");
    assertThat(storage.find("tickets/1/note.pdf")).contains(stored);

    storage.delete("tickets/1/note.pdf");
    assertThat(storage.find("tickets/1/note.pdf")).isEmpty();
  }

  @Test
  void default_storage_type_is_local() {
    ItsmStorageProperties props = new ItsmStorageProperties();
    assertThat(props.getType()).isEqualTo("local");
    assertThat(props.isS3()).isFalse();
    assertThat(props.getS3().getEndpoint()).isEqualTo("http://localhost:9000");
    assertThat(props.getS3().getBucket()).isEqualTo("itsm-attachments");
    assertThat(props.getS3().isPathStyleAccess()).isTrue();
  }

  @Test
  void storage_configuration_selects_local_when_not_s3() {
    ItsmStorageProperties props = new ItsmStorageProperties();
    props.setType("local");
    AttachmentStorage storage = new StorageConfiguration().attachmentStorage(props);
    assertThat(storage).isInstanceOf(StorageConfiguration.CloseableAttachmentStorage.class);
    AttachmentStorage delegate = ((StorageConfiguration.CloseableAttachmentStorage) storage).delegate();
    assertThat(delegate).isInstanceOf(LocalMetadataAttachmentStorage.class);
  }
}
