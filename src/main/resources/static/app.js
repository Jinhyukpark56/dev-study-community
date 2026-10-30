"use strict";

const state = {
    csrf: null,
    user: null,
    posts: [],
    post: null,
    comments: [],
    page: 0,
    size: 20,
    totalPages: 0,
    totalElements: 0,
    tag: "",
    editingPostId: null
};

const elements = {
    notice: document.querySelector("#notice"),
    authPanel: document.querySelector("#auth-panel"),
    sessionLabel: document.querySelector("#session-label"),
    showAuthButton: document.querySelector("#show-auth-button"),
    closeAuthButton: document.querySelector("#close-auth-button"),
    logoutButton: document.querySelector("#logout-button"),
    loginForm: document.querySelector("#login-form"),
    registerForm: document.querySelector("#register-form"),
    listView: document.querySelector("#list-view"),
    editorView: document.querySelector("#editor-view"),
    detailView: document.querySelector("#detail-view"),
    postList: document.querySelector("#post-list"),
    resultSummary: document.querySelector("#result-summary"),
    pageLabel: document.querySelector("#page-label"),
    previousPageButton: document.querySelector("#previous-page-button"),
    nextPageButton: document.querySelector("#next-page-button"),
    tagFilterForm: document.querySelector("#tag-filter-form"),
    tagFilter: document.querySelector("#tag-filter"),
    clearTagButton: document.querySelector("#clear-tag-button"),
    createPostButton: document.querySelector("#create-post-button"),
    postForm: document.querySelector("#post-form"),
    editorHeading: document.querySelector("#editor-heading"),
    postActions: document.querySelector("#post-actions"),
    editPostButton: document.querySelector("#edit-post-button"),
    deletePostButton: document.querySelector("#delete-post-button"),
    detailType: document.querySelector("#detail-type"),
    detailDate: document.querySelector("#detail-date"),
    detailAuthor: document.querySelector("#detail-author"),
    detailTitle: document.querySelector("#detail-title"),
    detailTags: document.querySelector("#detail-tags"),
    detailContent: document.querySelector("#detail-content"),
    commentForm: document.querySelector("#comment-form"),
    commentLoginGuide: document.querySelector("#comment-login-guide"),
    commentList: document.querySelector("#comment-list"),
    commentCount: document.querySelector("#comment-count"),
    listHeading: document.querySelector("#list-heading"),
    detailHeading: document.querySelector("#detail-title")
};

class ApiError extends Error {
    constructor(status, payload) {
        super(`HTTP ${status}`);
        this.name = "ApiError";
        this.status = status;
        this.payload = payload;
    }
}

function element(tagName, className, text) {
    const node = document.createElement(tagName);
    if (className) {
        node.className = className;
    }
    if (text !== undefined && text !== null) {
        node.textContent = String(text);
    }
    return node;
}

function showNotice(message, kind = "info") {
    elements.notice.textContent = message;
    elements.notice.className = `notice notice-${kind}`;
    elements.notice.hidden = false;
    elements.notice.scrollIntoView({behavior: "smooth", block: "nearest"});
}

function hideNotice() {
    elements.notice.hidden = true;
    elements.notice.textContent = "";
}

function errorMessage(error, action) {
    if (!(error instanceof ApiError)) {
        return `${action} 중 네트워크 오류가 발생했습니다. 연결을 확인해 주세요.`;
    }

    const messages = {
        400: "입력값을 확인해 주세요.",
        401: "로그인이 필요하거나 로그인 정보가 올바르지 않습니다.",
        403: "이 작업을 수행할 권한이 없거나 보안 토큰이 만료되었습니다. 다시 시도해 주세요.",
        404: "요청한 내용을 찾을 수 없습니다.",
        500: "서버에 문제가 생겼습니다. 잠시 후 다시 시도해 주세요."
    };

    return `${action} 실패: ${messages[error.status] || (error.status >= 500
        ? messages[500]
        : `서버가 ${error.status} 상태로 응답했습니다.`)}`;
}

async function readResponse(response) {
    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json") || contentType.includes("application/problem+json")) {
        try {
            return await response.json();
        } catch (_ignored) {
            return null;
        }
    }

    const text = await response.text();
    return text || null;
}

async function refreshCsrf() {
    const response = await fetch("/auth/csrf", {
        credentials: "same-origin",
        headers: {Accept: "application/json"}
    });
    const data = await readResponse(response);
    if (!response.ok) {
        throw new ApiError(response.status, data);
    }
    if (!data || !data.headerName || !data.token) {
        throw new Error("CSRF token response is incomplete");
    }
    state.csrf = data;
    return data;
}

async function request(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const unsafe = !["GET", "HEAD", "OPTIONS"].includes(method);
    if (unsafe && !state.csrf) {
        await refreshCsrf();
    }

    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");
    if (unsafe) {
        headers.set(state.csrf.headerName, state.csrf.token);
    }
    if (options.body instanceof URLSearchParams) {
        headers.set("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
    }

    let response;
    try {
        response = await fetch(path, {
            ...options,
            method,
            headers,
            credentials: "same-origin"
        });
    } catch (error) {
        throw error;
    }

    const data = await readResponse(response);
    if (!response.ok) {
        if (response.status === 401 && path !== "/auth/login") {
            state.user = null;
            updateAuthUi();
        }
        if (response.status === 403 && unsafe) {
            refreshCsrf()
                .then(loadSession)
                .catch(() => {
                    state.csrf = null;
                });
        }
        throw new ApiError(response.status, data);
    }
    return {response, data};
}

function normalizePage(data) {
    if (Array.isArray(data)) {
        return {
            content: data,
            page: 0,
            size: data.length,
            totalElements: data.length,
            totalPages: data.length > 0 ? 1 : 0,
            last: true
        };
    }

    const content = Array.isArray(data?.content)
        ? data.content
        : (Array.isArray(data?.posts) ? data.posts : []);
    const page = Number(data?.page ?? data?.number ?? 0);
    const size = Number(data?.size ?? state.size);
    const totalElements = Number(data?.totalElements ?? content.length);
    const totalPages = Number(data?.totalPages ?? (content.length > 0 ? 1 : 0));
    return {
        content,
        page: Number.isFinite(page) ? page : 0,
        size: Number.isFinite(size) && size > 0 ? size : state.size,
        totalElements: Number.isFinite(totalElements) ? totalElements : content.length,
        totalPages: Number.isFinite(totalPages) ? totalPages : 0,
        last: Boolean(data?.last ?? (page + 1 >= totalPages))
    };
}

function normalizeCollection(data) {
    if (Array.isArray(data)) {
        return data;
    }
    if (Array.isArray(data?.content)) {
        return data.content;
    }
    if (Array.isArray(data?.comments)) {
        return data.comments;
    }
    return [];
}

function tagName(tag) {
    if (typeof tag === "string") {
        return tag;
    }
    return tag?.name || tag?.tagName || "";
}

function tagsOf(post) {
    return Array.isArray(post?.tags)
        ? post.tags.map(tagName).map(name => name.trim()).filter(Boolean)
        : [];
}

function typeLabel(type) {
    if (type === "QUESTION") {
        return "질문";
    }
    if (type === "STUDY_LOG") {
        return "학습 기록";
    }
    return "게시글";
}

function formatDate(value) {
    if (!value) {
        return "";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return "";
    }
    return new Intl.DateTimeFormat("ko-KR", {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(date);
}

function authorLabel(item) {
    if (item?.authorEmail) {
        return item.authorEmail;
    }
    return item?.authorId !== undefined && item?.authorId !== null
        ? `작성자 #${item.authorId}`
        : "작성자 정보 없음";
}

function canManage(item) {
    if (!state.user) {
        return false;
    }
    if (state.user.id === undefined || state.user.id === null || item?.authorId === undefined || item?.authorId === null) {
        return false;
    }
    return String(state.user.id) === String(item.authorId);
}

function showView(name) {
    elements.listView.hidden = name !== "list";
    elements.editorView.hidden = name !== "editor";
    elements.detailView.hidden = name !== "detail";
    const heading = {
        list: elements.listHeading,
        editor: elements.editorHeading,
        detail: elements.detailHeading
    }[name];
    if (heading) {
        heading.tabIndex = -1;
        heading.focus({preventScroll: true});
    }
    window.scrollTo({top: 0, behavior: "smooth"});
}

function updateAuthUi() {
    const loggedIn = Boolean(state.user);
    elements.sessionLabel.textContent = loggedIn
        ? `${state.user.email} 로그인 중`
        : "로그인하지 않음";
    elements.showAuthButton.hidden = loggedIn;
    elements.logoutButton.hidden = !loggedIn;
    elements.createPostButton.hidden = !loggedIn;
    elements.commentForm.hidden = !loggedIn || !state.post;
    elements.commentLoginGuide.hidden = loggedIn || !state.post;
    elements.postActions.hidden = !state.post || !canManage(state.post);
}

async function loadSession() {
    try {
        const {data} = await request("/auth/me");
        state.user = data;
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
            state.user = null;
        } else {
            throw error;
        }
    }
    updateAuthUi();
}

function makeTagList(tags, interactive = false) {
    const container = element("div", "tag-list");
    for (const name of tags) {
        const chip = element(interactive ? "button" : "span", "tag-chip", `#${name}`);
        if (interactive) {
            chip.type = "button";
            chip.addEventListener("click", () => applyTag(name));
        }
        container.append(chip);
    }
    return container;
}

function renderPosts() {
    elements.postList.replaceChildren();
    if (state.posts.length === 0) {
        const message = state.tag
            ? `#${state.tag} 태그의 게시글이 없습니다.`
            : "아직 게시글이 없습니다. 첫 글을 작성해 보세요.";
        elements.postList.append(element("div", "empty-state", message));
    }

    for (const post of state.posts) {
        const card = element("article", "post-card");
        card.tabIndex = 0;
        card.setAttribute("role", "button");
        card.setAttribute("aria-label", `${post.title || "제목 없음"} 상세 보기`);
        const open = () => openPost(post.id);
        card.addEventListener("click", open);
        card.addEventListener("keydown", event => {
            if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                open();
            }
        });

        const top = element("div", "post-card-top");
        top.append(element("span", "type-badge", typeLabel(post.type)));
        const dateText = formatDate(post.createdAt);
        if (dateText) {
            top.append(element("span", "muted", dateText));
        }

        card.append(top);
        card.append(element("h2", "", post.title || "제목 없음"));
        card.append(element("p", "", post.content || ""));

        const bottom = element("div", "post-card-bottom");
        bottom.append(element("span", "", authorLabel(post)));
        bottom.append(makeTagList(tagsOf(post)));
        card.append(bottom);
        elements.postList.append(card);
    }

    const shownPage = state.totalPages === 0 ? 0 : state.page + 1;
    elements.pageLabel.textContent = `${shownPage} / ${state.totalPages} 페이지`;
    elements.previousPageButton.disabled = state.page <= 0;
    elements.nextPageButton.disabled = state.totalPages === 0 || state.page + 1 >= state.totalPages;
    elements.resultSummary.textContent = state.tag
        ? `#${state.tag} · 총 ${state.totalElements}개`
        : `총 ${state.totalElements}개`;
}

async function loadPosts() {
    const query = new URLSearchParams({
        page: String(state.page),
        size: String(state.size)
    });
    if (state.tag) {
        query.set("tag", state.tag);
    }

    try {
        const {data} = await request(`/posts?${query.toString()}`);
        const page = normalizePage(data);
        state.posts = page.content;
        state.page = page.page;
        state.size = page.size || state.size;
        state.totalElements = page.totalElements;
        state.totalPages = page.totalPages;
        renderPosts();
    } catch (error) {
        showNotice(errorMessage(error, "게시글 목록 조회"), "error");
        state.posts = [];
        state.totalElements = 0;
        state.totalPages = 0;
        renderPosts();
    }
}

async function applyTag(name) {
    state.tag = String(name || "").trim();
    state.page = 0;
    elements.tagFilter.value = state.tag;
    showView("list");
    await loadPosts();
}

function renderPostDetail() {
    const post = state.post;
    if (!post) {
        return;
    }

    elements.detailType.textContent = typeLabel(post.type);
    elements.detailDate.textContent = formatDate(post.updatedAt || post.createdAt);
    elements.detailAuthor.textContent = authorLabel(post);
    elements.detailTitle.textContent = post.title || "제목 없음";
    elements.detailContent.textContent = post.content || "";
    elements.detailTags.replaceChildren(...makeTagList(tagsOf(post), true).childNodes);
    updateAuthUi();
}

async function openPost(id) {
    hideNotice();
    try {
        const {data} = await request(`/posts/${encodeURIComponent(id)}`);
        state.post = data;
        renderPostDetail();
        showView("detail");
        await loadComments();
    } catch (error) {
        showNotice(errorMessage(error, "게시글 조회"), "error");
        if (error instanceof ApiError && error.status === 404) {
            showView("list");
            await loadPosts();
        }
    }
}

async function loadComments() {
    if (!state.post) {
        return;
    }
    try {
        const {data} = await request(`/posts/${encodeURIComponent(state.post.id)}/comments`);
        state.comments = normalizeCollection(data);
        renderComments();
    } catch (error) {
        state.comments = [];
        renderComments();
        showNotice(errorMessage(error, "댓글 조회"), "error");
    }
}

function renderComments() {
    elements.commentList.replaceChildren();
    elements.commentCount.textContent = `${state.comments.length}개`;
    if (state.comments.length === 0) {
        elements.commentList.append(element("div", "empty-state", "아직 댓글이 없습니다."));
        return;
    }

    for (const comment of state.comments) {
        const item = element("article", "comment-item");
        const head = element("div", "comment-head");
        const info = element("span", "", authorLabel(comment));
        const dateText = formatDate(comment.updatedAt || comment.createdAt);
        if (dateText) {
            info.textContent += ` · ${dateText}`;
        }
        head.append(info);

        if (canManage(comment)) {
            const actions = element("div", "action-row");
            const edit = element("button", "button button-quiet", "수정");
            edit.type = "button";
            edit.addEventListener("click", () => showCommentEditor(item, comment));
            const remove = element("button", "button button-danger", "삭제");
            remove.type = "button";
            remove.addEventListener("click", () => deleteComment(comment));
            actions.append(edit, remove);
            head.append(actions);
        }

        item.append(head, element("p", "", comment.content || ""));
        elements.commentList.append(item);
    }
}

function showCommentEditor(item, comment) {
    const form = element("form", "comment-edit-form");
    const textarea = element("textarea");
    textarea.name = "content";
    textarea.rows = 3;
    textarea.maxLength = 1000;
    textarea.required = true;
    textarea.setAttribute("aria-label", "댓글 내용");
    textarea.value = comment.content || "";

    const actions = element("div", "form-actions");
    const save = element("button", "button button-primary", "저장");
    save.type = "submit";
    const cancel = element("button", "button button-quiet", "취소");
    cancel.type = "button";
    cancel.addEventListener("click", renderComments);
    actions.append(save, cancel);
    form.append(textarea, actions);

    form.addEventListener("submit", async event => {
        event.preventDefault();
        const body = new URLSearchParams({content: textarea.value});
        save.disabled = true;
        try {
            await request(
                `/posts/${encodeURIComponent(state.post.id)}/comments/${encodeURIComponent(comment.id)}`,
                {method: "PATCH", body}
            );
            showNotice("댓글을 수정했습니다.", "success");
            await loadComments();
        } catch (error) {
            showNotice(errorMessage(error, "댓글 수정"), "error");
        } finally {
            save.disabled = false;
        }
    });

    item.replaceChildren(form);
    textarea.focus();
}

async function deleteComment(comment) {
    if (!window.confirm("이 댓글을 삭제할까요?")) {
        return;
    }
    try {
        await request(
            `/posts/${encodeURIComponent(state.post.id)}/comments/${encodeURIComponent(comment.id)}`,
            {method: "DELETE"}
        );
        showNotice("댓글을 삭제했습니다.", "success");
        await loadComments();
    } catch (error) {
        showNotice(errorMessage(error, "댓글 삭제"), "error");
    }
}

function openPostEditor(post = null) {
    if (!state.user) {
        elements.authPanel.hidden = false;
        showNotice("게시글을 작성하거나 수정하려면 로그인해 주세요.", "info");
        elements.loginForm.elements.email.focus();
        return;
    }

    state.editingPostId = post?.id ?? null;
    elements.editorHeading.textContent = post ? "게시글 수정" : "새 글 작성";
    elements.postForm.elements.type.value = post?.type || "QUESTION";
    elements.postForm.elements.title.value = post?.title || "";
    elements.postForm.elements.content.value = post?.content || "";
    elements.postForm.elements.tags.value = tagsOf(post).join(", ");
    showView("editor");
    elements.postForm.elements.title.focus();
}

function normalizedTags(input) {
    const seen = new Set();
    const tags = [];
    for (const raw of String(input || "").split(",")) {
        const name = raw.trim();
        const key = name.toLocaleLowerCase("ko-KR");
        if (name && !seen.has(key)) {
            seen.add(key);
            tags.push(name);
        }
    }
    return tags;
}

async function savePost(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = event.submitter || form.querySelector("button[type='submit']");
    const tags = normalizedTags(form.elements.tags.value);
    if (tags.length > 3) {
        showNotice("태그는 최대 3개까지 입력해 주세요.", "error");
        return;
    }

    const body = new URLSearchParams({
        title: form.elements.title.value,
        content: form.elements.content.value,
        type: form.elements.type.value,
        tags: tags.join(",")
    });
    const editing = state.editingPostId !== null;
    const path = editing ? `/posts/${encodeURIComponent(state.editingPostId)}` : "/posts";
    const method = editing ? "PATCH" : "POST";

    submit.disabled = true;
    try {
        const {response} = await request(path, {method, body});
        showNotice(editing ? "게시글을 수정했습니다." : "게시글을 작성했습니다.", "success");
        if (editing) {
            await openPost(state.editingPostId);
        } else {
            const location = response.headers.get("Location");
            const createdId = location?.split("/").filter(Boolean).pop();
            state.page = 0;
            await loadPosts();
            if (createdId) {
                await openPost(createdId);
            } else {
                showView("list");
            }
        }
    } catch (error) {
        showNotice(errorMessage(error, editing ? "게시글 수정" : "게시글 작성"), "error");
    } finally {
        submit.disabled = false;
    }
}

async function deletePost() {
    if (!state.post || !window.confirm("게시글과 연결된 댓글을 함께 삭제할까요?")) {
        return;
    }
    try {
        await request(`/posts/${encodeURIComponent(state.post.id)}`, {method: "DELETE"});
        state.post = null;
        state.comments = [];
        state.page = 0;
        showNotice("게시글을 삭제했습니다.", "success");
        showView("list");
        await loadPosts();
    } catch (error) {
        showNotice(errorMessage(error, "게시글 삭제"), "error");
    }
}

async function submitLogin(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = event.submitter || form.querySelector("button[type='submit']");
    const body = new URLSearchParams({
        email: form.elements.email.value,
        password: form.elements.password.value
    });
    submit.disabled = true;
    try {
        await request("/auth/login", {method: "POST", body});
        state.csrf = null;
        await refreshCsrf();
        await loadSession();
        form.reset();
        elements.authPanel.hidden = true;
        showNotice("로그인했습니다.", "success");
        if (state.post) {
            renderPostDetail();
            renderComments();
        }
    } catch (error) {
        showNotice(errorMessage(error, "로그인"), "error");
    } finally {
        submit.disabled = false;
    }
}

async function submitRegistration(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = event.submitter || form.querySelector("button[type='submit']");
    const email = form.elements.email.value;
    const body = new URLSearchParams({
        email,
        password: form.elements.password.value
    });
    submit.disabled = true;
    try {
        await request("/auth/register", {method: "POST", body});
        form.reset();
        elements.loginForm.elements.email.value = email.trim();
        elements.loginForm.elements.password.focus();
        showNotice("회원가입을 완료했습니다. 같은 정보로 로그인해 주세요.", "success");
    } catch (error) {
        showNotice(errorMessage(error, "회원가입"), "error");
    } finally {
        submit.disabled = false;
    }
}

async function logout() {
    elements.logoutButton.disabled = true;
    try {
        await request("/auth/logout", {method: "POST"});
        state.user = null;
        state.csrf = null;
        await refreshCsrf();
        updateAuthUi();
        if (state.post) {
            renderPostDetail();
            renderComments();
        }
        showNotice("로그아웃했습니다.", "success");
    } catch (error) {
        showNotice(errorMessage(error, "로그아웃"), "error");
    } finally {
        elements.logoutButton.disabled = false;
    }
}

function bindEvents() {
    document.querySelector(".brand").addEventListener("click", async event => {
        event.preventDefault();
        showView("list");
        await loadPosts();
    });
    document.querySelectorAll(".back-to-list").forEach(button => {
        button.addEventListener("click", () => showView("list"));
    });
    elements.showAuthButton.addEventListener("click", () => {
        elements.authPanel.hidden = false;
        elements.loginForm.elements.email.focus();
    });
    elements.closeAuthButton.addEventListener("click", () => {
        elements.authPanel.hidden = true;
    });
    elements.logoutButton.addEventListener("click", logout);
    elements.loginForm.addEventListener("submit", submitLogin);
    elements.registerForm.addEventListener("submit", submitRegistration);
    elements.createPostButton.addEventListener("click", () => openPostEditor());
    elements.editPostButton.addEventListener("click", () => openPostEditor(state.post));
    elements.deletePostButton.addEventListener("click", deletePost);
    elements.postForm.addEventListener("submit", savePost);
    elements.commentForm.addEventListener("submit", async event => {
        event.preventDefault();
        const submit = event.submitter || event.currentTarget.querySelector("button[type='submit']");
        const textarea = event.currentTarget.elements.content;
        const body = new URLSearchParams({content: textarea.value});
        submit.disabled = true;
        try {
            await request(`/posts/${encodeURIComponent(state.post.id)}/comments`, {method: "POST", body});
            textarea.value = "";
            showNotice("댓글을 작성했습니다.", "success");
            await loadComments();
        } catch (error) {
            showNotice(errorMessage(error, "댓글 작성"), "error");
        } finally {
            submit.disabled = false;
        }
    });
    elements.tagFilterForm.addEventListener("submit", event => {
        event.preventDefault();
        applyTag(elements.tagFilter.value);
    });
    elements.clearTagButton.addEventListener("click", () => applyTag(""));
    elements.previousPageButton.addEventListener("click", async () => {
        if (state.page > 0) {
            state.page -= 1;
            await loadPosts();
            window.scrollTo({top: 0, behavior: "smooth"});
        }
    });
    elements.nextPageButton.addEventListener("click", async () => {
        if (state.page + 1 < state.totalPages) {
            state.page += 1;
            await loadPosts();
            window.scrollTo({top: 0, behavior: "smooth"});
        }
    });
}

async function initialize() {
    bindEvents();
    showView("list");
    const csrfResult = await Promise.allSettled([refreshCsrf(), loadSession()]);
    for (const result of csrfResult) {
        if (result.status === "rejected") {
            showNotice(errorMessage(result.reason, "초기 연결"), "error");
            break;
        }
    }
    await loadPosts();
}

initialize();
