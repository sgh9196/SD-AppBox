package com.sanaiddalgi.hub.blog.naver.model;

/** 네이버 에디터에 순서대로 넣을 원고 블록. */
public class DraftBlock {

    public enum Type {
        TEXT, IMAGE
    }

    /** 텍스트 블록의 역할 — 줄바꿈·간격 규칙이 다름. */
    public enum TextRole {
        /** 일반 문단 */
        PARAGRAPH,
        /** 인트로 고정 문구 */
        INTRO,
        /** 🏠·📋·✨·💡 등 섹션 제목(한 줄) */
        SECTION_TITLE,
        /** 📍 장소 정보 — 항목별 줄바꿈 */
        PLACE_INFO
    }

    private Type type;
    private TextRole textRole = TextRole.PARAGRAPH;
    /** 사진 직후 설명 — 앞에 Enter 없이 이어 붙임 */
    private boolean tightAfterPhoto;
    private String text;
    private String imagePath;

    public static DraftBlock text(String text) {
        DraftBlock block = new DraftBlock();
        block.type = Type.TEXT;
        block.text = text;
        return block;
    }

    public static DraftBlock caption(String text) {
        DraftBlock block = text(text);
        block.tightAfterPhoto = true;
        return block;
    }

    public static DraftBlock intro(String text) {
        DraftBlock block = text(text);
        block.textRole = TextRole.INTRO;
        return block;
    }

    public static DraftBlock sectionTitle(String text) {
        DraftBlock block = text(text);
        block.textRole = TextRole.SECTION_TITLE;
        return block;
    }

    public static DraftBlock placeInfo(String text) {
        DraftBlock block = text(text);
        block.textRole = TextRole.PLACE_INFO;
        return block;
    }

    public static DraftBlock image(String imagePath) {
        DraftBlock block = new DraftBlock();
        block.type = Type.IMAGE;
        block.imagePath = imagePath;
        return block;
    }

    public Type getType() {
        return type;
    }

    public TextRole getTextRole() {
        return textRole;
    }

    public boolean isTightAfterPhoto() {
        return tightAfterPhoto;
    }

    public String getText() {
        return text;
    }

    public String getImagePath() {
        return imagePath;
    }
}
