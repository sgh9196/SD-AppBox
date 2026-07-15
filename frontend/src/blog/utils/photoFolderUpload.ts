/** 사진 마커 카테고리 — BlogPage·API 필드명과 동일 */
export type PhotoCategory = 'external' | 'interior' | 'menu' | 'product';

export const PHOTO_CATEGORIES: PhotoCategory[] = ['external', 'interior', 'menu', 'product'];

const CATEGORY_FOLDER_ALIASES: Record<PhotoCategory, string[]> = {
  external: ['external', 'exterior', '외관', '외부'],
  interior: ['interior', 'inside', '내부', '인테리어'],
  menu: ['menu', 'menus', '메뉴', '메뉴판'],
  product: ['product', 'products', 'food', '음식', '음료', '상품'],
};

const IMAGE_NAME_RE = /\.(jpe?g|png|webp|gif|bmp|heic|heif)$/i;

function normalizeSegment(segment: string): string {
  return segment.trim().toLowerCase().replace(/\s+/g, '');
}

function matchesFolderSegment(segment: string, aliases: string[]): boolean {
  const norm = normalizeSegment(segment);
  if (!norm) return false;
  return aliases.some((alias) => {
    const key = normalizeSegment(alias);
    return norm === key || norm.startsWith(`${key}_`) || norm.startsWith(`${key}-`);
  });
}

/** 상대 경로의 폴더명으로 카테고리 추론 (예: review/external/1.jpg → external) */
export function classifyPhotoCategory(relativePath: string): PhotoCategory | null {
  const normalized = relativePath.replace(/\\/g, '/');
  const folders = normalized.split('/').slice(0, -1);
  for (const folder of folders) {
    for (const cat of PHOTO_CATEGORIES) {
      if (matchesFolderSegment(folder, CATEGORY_FOLDER_ALIASES[cat])) {
        return cat;
      }
    }
  }
  return null;
}

function relativePathOf(file: File): string {
  const withPath = file as File & { webkitRelativePath?: string };
  return withPath.webkitRelativePath || file.name;
}

function isImageFile(file: File): boolean {
  if (file.type.startsWith('image/')) return true;
  return IMAGE_NAME_RE.test(file.name);
}

export type FolderUploadResult = {
  grouped: Record<PhotoCategory, File[]>;
  skipped: File[];
};

/** 폴더 선택 결과를 카테고리별 File[] 로 분류 (파일명 순 정렬) */
export function groupFolderImages(files: FileList | File[]): FolderUploadResult {
  const grouped: Record<PhotoCategory, File[]> = {
    external: [],
    interior: [],
    menu: [],
    product: [],
  };
  const skipped: File[] = [];

  for (const file of Array.from(files)) {
    if (!isImageFile(file)) {
      skipped.push(file);
      continue;
    }
    const category = classifyPhotoCategory(relativePathOf(file));
    if (category == null) {
      skipped.push(file);
      continue;
    }
    grouped[category].push(file);
  }

  for (const cat of PHOTO_CATEGORIES) {
    grouped[cat].sort((a, b) => relativePathOf(a).localeCompare(relativePathOf(b), 'ko'));
  }

  return { grouped, skipped };
}

const CATEGORY_LABELS: Record<PhotoCategory, string> = {
  external: '외관',
  interior: '내부',
  menu: '메뉴',
  product: '음식',
};

export function summarizeFolderUpload(result: FolderUploadResult): string {
  const parts = PHOTO_CATEGORIES.filter((cat) => result.grouped[cat].length > 0).map(
    (cat) => `${CATEGORY_LABELS[cat]} ${result.grouped[cat].length}장`,
  );
  if (parts.length === 0) {
    return '등록된 사진이 없습니다. external·interior·menu·product(또는 외관·내부·메뉴·음식) 폴더를 확인해 주세요.';
  }
  const skippedNote =
    result.skipped.length > 0 ? ` · 분류 불가 ${result.skipped.length}개 제외` : '';
  return `폴더에서 ${parts.join(', ')} 등록${skippedNote}`;
}
