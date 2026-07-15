package com.sentinel.enforcement.persistence.report;

import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

  @Update(
      """
            <script>
            UPDATE report
            SET
                status = #{report.status},
                updated_at = #{report.updatedAt},
                updated_by = #{report.updatedBy},
                version = #{report.version}
            WHERE id = #{report.id}
              AND version = #{expectedVersion}
            </script>
            """)
  int update(
      @Param("report") ReportRecord reportRecord, @Param("expectedVersion") long expectedVersion);

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
