from pathlib import Path


def replace_once(path_str: str, old: str, new: str) -> None:
    path = Path(path_str)
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, found {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1))


policy = "app/src/main/java/app/ownplay/mobile/feature/library/ui/LibraryBrowsePolicy.kt"
replace_once(
    policy,
    '''internal object LibraryBrowsePolicy {
    fun visibleCategories''',
    '''internal object LibraryBrowsePolicy {
    const val HOME_PREVIEW_LIMIT = 10

    fun <T> homePreview(items: List<T>): List<T> = items.take(HOME_PREVIEW_LIMIT)

    fun visibleCategories''',
)

test = "app/src/test/java/app/ownplay/mobile/feature/library/ui/LibraryBrowsePolicyTest.kt"
replace_once(
    test,
    '''        assertEquals("action", LibraryBrowsePolicy.activeCategoryKey(visible, null))
    }
}''',
    '''        assertEquals("action", LibraryBrowsePolicy.activeCategoryKey(visible, null))
    }

    @Test
    fun `library home preview caps category rows at ten`() {
        val items = (1..14).toList()
        assertEquals((1..10).toList(), LibraryBrowsePolicy.homePreview(items))
        assertEquals(10, LibraryBrowsePolicy.HOME_PREVIEW_LIMIT)
    }
}''',
)
