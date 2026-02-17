package com.telecom.mediation.repository;

import com.telecom.mediation.model.CdrEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for CDR entities.
 *
 * JpaRepository<CdrEntity, String>: CRUD + flush/saveAndFlush; first type is
 * entity, second is PK type. We get save(), findById(), findAll(), delete(), etc.
 * without writing SQL. Spring generates implementation at runtime.
 *
 * In other frameworks: Quarkus has Panache (PanacheRepository); Micronaut has
 * JpaRepository-style; in Node you'd use TypeORM/Prisma repository.
 */
public interface CdrRepository extends JpaRepository<CdrEntity, String> {
}
