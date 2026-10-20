package com.jinhyuk.community.post;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Override
    @EntityGraph(attributePaths = "author")
    Page<Post> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"author", "tags"})
    Optional<Post> findById(Long id);

    @Query(
            value = "select distinct post from Post post join post.tags tag where tag.name = :tagName",
            countQuery = "select count(distinct post) from Post post join post.tags tag where tag.name = :tagName")
    @EntityGraph(attributePaths = "author")
    Page<Post> findAllByTagName(@Param("tagName") String tagName, Pageable pageable);
}
