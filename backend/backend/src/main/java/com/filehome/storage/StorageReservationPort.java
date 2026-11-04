package com.filehome.storage;

import java.util.UUID;

public interface StorageReservationPort {

    long reservedBytes(UUID ownerId);
}
