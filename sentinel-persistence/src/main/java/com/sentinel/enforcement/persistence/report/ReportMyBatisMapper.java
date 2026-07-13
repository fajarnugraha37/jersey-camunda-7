package com.sentinel.enforcement.persistence.report;

import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReportMyBatisMapper {

  @Insert(
      """
            INSERT INTO report (
                id,
                title,
                description,
                jurisdiction_code,
                reporter_name,
                status,
                created_at,
                created_by,
                updated_at,
                updated_by,
                version
            ) VALUES (
                #{id},
                #{title},
                #{description},
                #{jurisdictionCode},
                #{reporterName},
                #{status},
                #{createdAt},
                #{createdBy},
                #{updatedAt},
                #{updatedBy},
                #{version}
            )
            """)
  int insert(ReportRecord reportRecord);

  @Select(
      """
            SELECT
                id,
                title,
                description,
                jurisdiction_code AS jurisdictionCode,
                reporter_name AS reporterName,
                status,
                created_at AS createdAt,
                created_by AS createdBy,
                updated_at AS updatedAt,
                updated_by AS updatedBy,
                version
            FROM report
            WHERE id = #{id}
            """)
  ReportRecord findById(UUID id);
}
