package com.jinhyuk.community.comment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByCreatedAtAscIdAsc(Long postId);

    Optional<Comment> findByIdAndPostId(Long id, Long postId);

    @Modifying
    @Query("delete from Comment comment where comment.post.id = :postId")
    int deleteAllByPostId(@Param("postId") Long postId);
}
