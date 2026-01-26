#!/bin/zsh

# ============================================================================
# Git Hooks 설치 스크립트 (Simple Version)
# ============================================================================

echo "🚀 Git Hooks 설치 시작..."

# Git 루트 찾기
PROJECT_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)

if [ -z "$PROJECT_ROOT" ]; then
    echo "❌ Git 저장소를 찾을 수 없습니다."
    exit 1
fi

echo "📂 프로젝트 루트: $PROJECT_ROOT"

# .git/hooks 디렉토리 생성
HOOKS_DIR="$PROJECT_ROOT/.git/hooks"
mkdir -p "$HOOKS_DIR"

# 복사할 파일 목록
FILES=("pre-commit" "prepare-commit-msg")

# 파일 복사 및 권한 설정
echo ""
echo "📋 Hook 파일 복사 중..."
for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        cp "$file" "$HOOKS_DIR/"
        chmod +x "$HOOKS_DIR/$file"
        echo "  ✅ $file 복사 완료"
    else
        echo "  ⚠️  $file 파일이 없습니다."
    fi
done

# 필요한 파일 생성
echo ""
echo "📝 필요한 파일 생성 중..."

# GEMINI_REPORT.md 초기 파일 생성 (없으면)
REPORT_FILE="$PROJECT_ROOT/GEMINI_REPORT.md"
if [ ! -f "$REPORT_FILE" ]; then
    cat > "$REPORT_FILE" << 'EOF'
# 🤖 Gemini 코드 리뷰 리포트

> 이 파일은 Git pre-commit hook 실행 시 자동으로 업데이트됩니다.

---

EOF
    chmod 666 "$REPORT_FILE"
    echo "  ✅ GEMINI_REPORT.md 생성 완료"
else
    echo "  ℹ️  GEMINI_REPORT.md 이미 존재함 (건너뜀)"
fi

# GEMINI_MSG_TMP 파일 생성 (빈 파일)
TMP_MSG_FILE="$PROJECT_ROOT/.git/GEMINI_MSG_TMP"
touch "$TMP_MSG_FILE"
chmod 666 "$TMP_MSG_FILE"
echo "  ✅ GEMINI_MSG_TMP 생성 완료"

# .gitignore 업데이트 (리포트 파일 추가)
GITIGNORE="$PROJECT_ROOT/.gitignore"
if [ -f "$GITIGNORE" ]; then
    if ! grep -q "GEMINI_REPORT.md" "$GITIGNORE"; then
        echo "" >> "$GITIGNORE"
        echo "# AI Code Review Reports" >> "$GITIGNORE"
        echo "GEMINI_REPORT.md" >> "$GITIGNORE"
        echo "  ✅ .gitignore에 GEMINI_REPORT.md 추가"
    else
        echo "  ℹ️  .gitignore에 이미 GEMINI_REPORT.md 존재"
    fi
else
    cat > "$GITIGNORE" << 'EOF'
# AI Code Review Reports
GEMINI_REPORT.md
EOF
    echo "  ✅ .gitignore 생성 및 설정 완료"
fi

echo ""
echo "🎉 설치 완료!"
echo ""
echo "📋 설치된 항목:"
echo "  - .git/hooks/pre-commit"
echo "  - .git/hooks/prepare-commit-msg"
echo "  - GEMINI_REPORT.md (리포트 파일)"
echo "  - .git/GEMINI_MSG_TMP (임시 메시지 파일)"