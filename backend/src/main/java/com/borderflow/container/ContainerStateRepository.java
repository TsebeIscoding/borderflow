package com.borderflow.container;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ContainerStateRepository extends JpaRepository<ContainerState, UUID> {
}
