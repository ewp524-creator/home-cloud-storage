package com.filehome.upload;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.filehome.storage.StorageReservationPort;

@Component
public class MultipartUploadReservationAdapter
        implements StorageReservationPort {

    private final MultipartUploadRepository repository;

    public MultipartUploadReservationAdapter(
            MultipartUploadRepository repository) {
        this.repository = repository;
    }

    @Override
    public long reservedBytes(UUID ownerId) {
        return repository.sumReservedBytes(ownerId);
    }
}
