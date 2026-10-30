package com.jinhyuk.community.comment;

public class CommentCreationResult {

    private final CommentOperationResult result;
    private final Comment comment;

    private CommentCreationResult(CommentOperationResult result, Comment comment) {
        this.result = result;
        this.comment = comment;
    }

    public static CommentCreationResult success(Comment comment) {
        return new CommentCreationResult(CommentOperationResult.SUCCESS, comment);
    }

    public static CommentCreationResult failure(CommentOperationResult result) {
        return new CommentCreationResult(result, null);
    }

    public CommentOperationResult getResult() {
        return result;
    }

    public Comment getComment() {
        return comment;
    }
}
