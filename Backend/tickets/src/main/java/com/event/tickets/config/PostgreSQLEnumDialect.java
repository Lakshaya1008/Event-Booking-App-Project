package com.event.tickets.config;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

/**
 * Custom PostgreSQL dialect that properly handles ENUM types.
 *
 * <p>Hibernate's default PostgreSQL dialect tries to create SQL ENUM types,
 * which fails in ddl-auto=update mode if the type doesn't exist.
 *
 * <p>This dialect maps Java enums to VARCHAR instead, which:
 * <ul>
 *   <li>Works with Hibernate's ddl-auto=update (no migration scripts needed)</li>
 *   <li>Maintains type safety at application level via @Enumerated(STRING)</li>
 *   <li>Allows CHECK constraints for database-level validation</li>
 *   <li>Avoids "type does not exist" errors during schema updates</li>
 * </ul>
 */
public class PostgreSQLEnumDialect extends PostgreSQLDialect {

  @Override
  public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
    super.contributeTypes(typeContributions, serviceRegistry);

    // Register VARCHAR as the JDBC type for enums instead of PostgreSQL ENUM
    JdbcTypeRegistry jdbcTypeRegistry = typeContributions.getTypeConfiguration()
        .getJdbcTypeRegistry();

    // This tells Hibernate to use VARCHAR(255) instead of creating custom ENUM types
    // The CHECK constraint in @Column annotation provides DB-level validation
    jdbcTypeRegistry.addDescriptor(SqlTypes.NAMED_ENUM,
        jdbcTypeRegistry.getDescriptor(SqlTypes.VARCHAR));
  }
}
