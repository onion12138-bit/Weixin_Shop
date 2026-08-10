package com.diybracelet.mall.recommendation.infrastructure;

import com.diybracelet.mall.recommendation.domain.AiRecommendationLog;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Admin recommendation-log persistence with bounded list queries. */
@Mapper
public interface AiRecommendationLogMapper {

    @Insert("""
        insert into ai_recommendation_log (
            trace_id, status, wish_summary, request_json
        ) values (
            #{traceId}, #{status}, #{wishSummary}, #{requestJson}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiRecommendationLog log);

    @Update("""
        update ai_recommendation_log
        set status = #{status},
            model_name = #{modelName},
            preference_summary = #{preferenceSummary},
            candidate_count = #{candidateCount},
            matched_candidate_count = #{matchedCandidateCount},
            matched_plan_count = #{matchedPlanCount},
            plan_count = #{planCount},
            attempt_count = #{attemptCount},
            duration_ms = #{durationMs},
            error_code = #{errorCode},
            error_message = #{errorMessage},
            config_snapshot_json = #{configSnapshotJson},
            candidates_json = #{candidatesJson},
            attempts_json = #{attemptsJson},
            preference_analysis_json = #{preferenceAnalysisJson},
            final_response_json = #{finalResponseJson},
            updated_at = current_timestamp
        where id = #{id}
        """)
    int update(AiRecommendationLog log);

    @Select("select * from ai_recommendation_log where id = #{id}")
    AiRecommendationLog selectById(@Param("id") long id);

    @Select("""
        <script>
        select * from ai_recommendation_log
        where 1 = 1
        <if test="status != null and status != ''">
          and status = #{status}
        </if>
        <if test="keyword != null and keyword != ''">
          and (
            trace_id like concat('%', #{keyword}, '%')
            or wish_summary like concat('%', #{keyword}, '%')
            or preference_summary like concat('%', #{keyword}, '%')
          )
        </if>
        order by id desc
        limit #{limit} offset #{offset}
        </script>
        """)
    List<AiRecommendationLog> selectPage(
        @Param("status") String status,
        @Param("keyword") String keyword,
        @Param("offset") long offset,
        @Param("limit") int limit
    );

    @Select("""
        <script>
        select count(*) from ai_recommendation_log
        where 1 = 1
        <if test="status != null and status != ''">
          and status = #{status}
        </if>
        <if test="keyword != null and keyword != ''">
          and (
            trace_id like concat('%', #{keyword}, '%')
            or wish_summary like concat('%', #{keyword}, '%')
            or preference_summary like concat('%', #{keyword}, '%')
          )
        </if>
        </script>
        """)
    long count(@Param("status") String status, @Param("keyword") String keyword);

    @Delete("delete from ai_recommendation_log where id = #{id}")
    int deleteById(@Param("id") long id);
}
