package com.borderflow.client;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ClientCoreRepository extends JpaRepository<ClientCore, UUID> {
}
