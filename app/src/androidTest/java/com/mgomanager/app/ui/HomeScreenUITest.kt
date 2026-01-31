package com.mgomanager.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.SusLevel
import com.mgomanager.app.ui.components.GlobalHeader
import com.mgomanager.app.ui.screens.home.*
import com.mgomanager.app.ui.theme.MGOManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Tests for HomeScreen (P2 - Startseite)
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Test data
    private val testAccount1 = Account(
        id = 1,
        accountName = "TestAccount1",
        prefix = "MGO_",
        createdAt = 1000L,
        lastPlayedAt = 5000L,
        userId = "user123456",
        backupPath = "/storage/emulated/0/bgo_backups/MGO_TestAccount1/",
        fileOwner = "u0_a123",
        fileGroup = "u0_a123",
        filePermissions = "rwxr-xr-x"
    )

    private val testAccount2 = Account(
        id = 2,
        accountName = "AnotherAccount",
        prefix = "MGO_",
        createdAt = 2000L,
        lastPlayedAt = 3000L,
        userId = "user789012",
        backupPath = "/storage/emulated/0/bgo_backups/MGO_AnotherAccount/",
        fileOwner = "u0_a123",
        fileGroup = "u0_a123",
        filePermissions = "rwxr-xr-x"
    )

    private val testAccountWithError = Account(
        id = 3,
        accountName = "ErrorAccount",
        prefix = "MGO_",
        createdAt = 3000L,
        lastPlayedAt = 1000L,
        userId = "user345678",
        hasError = true,
        backupPath = "/storage/emulated/0/bgo_backups/MGO_ErrorAccount/",
        fileOwner = "u0_a123",
        fileGroup = "u0_a123",
        filePermissions = "rwxr-xr-x"
    )

    private val testAccountWithSus = Account(
        id = 4,
        accountName = "SusAccount",
        prefix = "BGO_",
        createdAt = 4000L,
        lastPlayedAt = 2000L,
        userId = "user567890",
        susLevel = SusLevel.LEVEL_3,
        backupPath = "/storage/emulated/0/bgo_backups/BGO_SusAccount/",
        fileOwner = "u0_a123",
        fileGroup = "u0_a123",
        filePermissions = "rwxr-xr-x"
    )

    // ============================================================
    // GlobalHeader Tests
    // ============================================================

    @Test
    fun globalHeader_displaysBabixGO() {
        composeTestRule.setContent {
            MGOManagerTheme {
                GlobalHeader()
            }
        }

        composeTestRule.onNodeWithText("babixGO").assertIsDisplayed()
    }

    @Test
    fun globalHeader_displaysHelpIcon() {
        composeTestRule.setContent {
            MGOManagerTheme {
                GlobalHeader()
            }
        }

        composeTestRule.onNodeWithContentDescription("Hilfe").assertIsDisplayed()
    }

    @Test
    fun globalHeader_displaysSubtitle_whenProvided() {
        composeTestRule.setContent {
            MGOManagerTheme {
                GlobalHeader(subTitle = "Accounts")
            }
        }

        composeTestRule.onNodeWithText("Accounts").assertIsDisplayed()
    }

    @Test
    fun globalHeader_helpDialog_showsPlaceholder() {
        composeTestRule.setContent {
            MGOManagerTheme {
                GlobalHeader()
            }
        }

        // Click help icon
        composeTestRule.onNodeWithContentDescription("Hilfe").performClick()

        // Verify dialog shows placeholder text
        composeTestRule.onNodeWithText("Hilfe").assertIsDisplayed()
        composeTestRule.onNodeWithText("Platzhalter – Folgt noch..").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun globalHeader_helpDialog_closesOnOK() {
        composeTestRule.setContent {
            MGOManagerTheme {
                GlobalHeader()
            }
        }

        // Open dialog
        composeTestRule.onNodeWithContentDescription("Hilfe").performClick()
        composeTestRule.onNodeWithText("Platzhalter – Folgt noch..").assertIsDisplayed()

        // Close dialog
        composeTestRule.onNodeWithText("OK").performClick()

        // Verify dialog is closed
        composeTestRule.onNodeWithText("Platzhalter – Folgt noch..").assertDoesNotExist()
    }

    // ============================================================
    // CurrentAccountSection Tests
    // ============================================================

    @Test
    fun currentAccountSection_displaysAccountDetails_whenAccountExists() {
        composeTestRule.setContent {
            MGOManagerTheme {
                CurrentAccountSection(
                    currentAccount = testAccount1,
                    isLoading = false,
                    onLaunchMonopolyGo = {}
                )
            }
        }

        composeTestRule.onNodeWithText("AKTUELLER ACCOUNT:").assertIsDisplayed()
        composeTestRule.onNodeWithText("MGO_TestAccount1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Starte Monopoly Go").assertIsDisplayed()
    }

    @Test
    fun currentAccountSection_displaysUserId() {
        composeTestRule.setContent {
            MGOManagerTheme {
                CurrentAccountSection(
                    currentAccount = testAccount1,
                    isLoading = false,
                    onLaunchMonopolyGo = {}
                )
            }
        }

        composeTestRule.onNodeWithText("ID: user123456", substring = true).assertIsDisplayed()
    }

    @Test
    fun currentAccountSection_displaysNoDataMessage_whenNoAccount() {
        composeTestRule.setContent {
            MGOManagerTheme {
                CurrentAccountSection(
                    currentAccount = null,
                    isLoading = false,
                    onLaunchMonopolyGo = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Noch keine Daten vorhanden..").assertIsDisplayed()
    }

    @Test
    fun currentAccountSection_showsLoadingIndicator_whenLoading() {
        composeTestRule.setContent {
            MGOManagerTheme {
                CurrentAccountSection(
                    currentAccount = testAccount1,
                    isLoading = true,
                    onLaunchMonopolyGo = {}
                )
            }
        }

        composeTestRule.onNode(hasProgressBarRangeInfo(0f..1f)).assertExists()
    }

    @Test
    fun currentAccountSection_launchButtonDisabled_whenLoading() {
        composeTestRule.setContent {
            MGOManagerTheme {
                CurrentAccountSection(
                    currentAccount = testAccount1,
                    isLoading = true,
                    onLaunchMonopolyGo = {}
                )
            }
        }

        // The button container should exist but be disabled
        composeTestRule.onNodeWithText("Starte Monopoly Go").assertDoesNotExist()
    }

    @Test
    fun currentAccountSection_launchButtonCallsCallback() {
        var clickedId: Long? = null
        composeTestRule.setContent {
            MGOManagerTheme {
                CurrentAccountSection(
                    currentAccount = testAccount1,
                    isLoading = false,
                    onLaunchMonopolyGo = { clickedId = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Starte Monopoly Go").performClick()

        assert(clickedId == 1L)
    }

    // ============================================================
    // SearchAndSortSection Tests
    // ============================================================

    @Test
    fun searchAndSortSection_displaysSearchField() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SearchAndSortSection(
                    searchQuery = "",
                    sortOption = SortOption.LAST_PLAYED,
                    sortDirection = SortDirection.DESC,
                    onSearchQueryChange = {},
                    onSortOptionChange = {},
                    onToggleSortDirection = {},
                    sortDropdownExpanded = false,
                    onSortDropdownExpandedChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Account suchen...").assertIsDisplayed()
    }

    @Test
    fun searchAndSortSection_displaysSortButton() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SearchAndSortSection(
                    searchQuery = "",
                    sortOption = SortOption.LAST_PLAYED,
                    sortDirection = SortDirection.DESC,
                    onSearchQueryChange = {},
                    onSortOptionChange = {},
                    onToggleSortDirection = {},
                    sortDropdownExpanded = false,
                    onSortDropdownExpandedChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Sortiere nach: Zuletzt gespielt", substring = true).assertIsDisplayed()
    }

    @Test
    fun searchAndSortSection_displaysSortDirection() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SearchAndSortSection(
                    searchQuery = "",
                    sortOption = SortOption.LAST_PLAYED,
                    sortDirection = SortDirection.DESC,
                    onSearchQueryChange = {},
                    onSortOptionChange = {},
                    onToggleSortDirection = {},
                    sortDropdownExpanded = false,
                    onSortDropdownExpandedChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("DESC").assertIsDisplayed()
    }

    @Test
    fun searchAndSortSection_showsClearButton_whenQueryNotEmpty() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SearchAndSortSection(
                    searchQuery = "test",
                    sortOption = SortOption.LAST_PLAYED,
                    sortDirection = SortDirection.DESC,
                    onSearchQueryChange = {},
                    onSortOptionChange = {},
                    onToggleSortDirection = {},
                    sortDropdownExpanded = false,
                    onSortDropdownExpandedChange = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Löschen").assertIsDisplayed()
    }

    @Test
    fun searchAndSortSection_sortDropdown_showsOptions_whenExpanded() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SearchAndSortSection(
                    searchQuery = "",
                    sortOption = SortOption.LAST_PLAYED,
                    sortDirection = SortDirection.DESC,
                    onSearchQueryChange = {},
                    onSortOptionChange = {},
                    onToggleSortDirection = {},
                    sortDropdownExpanded = true,
                    onSortDropdownExpandedChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zuletzt gespielt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Erstellt am").assertIsDisplayed()
        composeTestRule.onNodeWithText("User ID").assertIsDisplayed()
    }

    @Test
    fun searchAndSortSection_searchQueryChange_callsCallback() {
        var capturedQuery = ""
        composeTestRule.setContent {
            MGOManagerTheme {
                SearchAndSortSection(
                    searchQuery = "",
                    sortOption = SortOption.LAST_PLAYED,
                    sortDirection = SortDirection.DESC,
                    onSearchQueryChange = { capturedQuery = it },
                    onSortOptionChange = {},
                    onToggleSortDirection = {},
                    sortDropdownExpanded = false,
                    onSortDropdownExpandedChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Account suchen...").performTextInput("test")

        assert(capturedQuery == "test")
    }

    // ============================================================
    // AccountListSection Tests
    // ============================================================

    @Test
    fun accountListSection_displaysAccounts() {
        composeTestRule.setContent {
            MGOManagerTheme {
                AccountListSection(
                    accounts = listOf(testAccount1, testAccount2),
                    searchQuery = "",
                    onAccountClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("MGO_TestAccount1").assertIsDisplayed()
        composeTestRule.onNodeWithText("MGO_AnotherAccount").assertIsDisplayed()
    }

    @Test
    fun accountListSection_showsNoResultsMessage_whenEmptyWithSearchQuery() {
        composeTestRule.setContent {
            MGOManagerTheme {
                AccountListSection(
                    accounts = emptyList(),
                    searchQuery = "nonexistent",
                    onAccountClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Kein Account mit diesem Namen gefunden.").assertIsDisplayed()
    }

    @Test
    fun accountListSection_showsNoBackupsMessage_whenEmptyWithoutSearchQuery() {
        composeTestRule.setContent {
            MGOManagerTheme {
                AccountListSection(
                    accounts = emptyList(),
                    searchQuery = "",
                    onAccountClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Noch keine Backups vorhanden.").assertIsDisplayed()
    }

    @Test
    fun accountListSection_accountClick_callsCallback() {
        var clickedId: Long? = null
        composeTestRule.setContent {
            MGOManagerTheme {
                AccountListSection(
                    accounts = listOf(testAccount1),
                    searchQuery = "",
                    onAccountClick = { clickedId = it }
                )
            }
        }

        composeTestRule.onNodeWithText("MGO_TestAccount1").performClick()

        assert(clickedId == 1L)
    }

    // ============================================================
    // AccountListCard Tests
    // ============================================================

    @Test
    fun accountListCard_displaysAccountName() {
        composeTestRule.setContent {
            MGOManagerTheme {
                AccountListCard(
                    account = testAccount1,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("MGO_TestAccount1").assertIsDisplayed()
    }

    @Test
    fun accountListCard_displaysShortUserId() {
        composeTestRule.setContent {
            MGOManagerTheme {
                AccountListCard(
                    account = testAccount1,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("ID: ...3456", substring = true).assertIsDisplayed()
    }

    @Test
    fun accountListCard_showsErrorIcon_whenHasError() {
        composeTestRule.setContent {
            MGOManagerTheme {
                AccountListCard(
                    account = testAccountWithError,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Fehler").assertIsDisplayed()
    }

    @Test
    fun accountListCard_showsWarningIcon_whenHasSus() {
        composeTestRule.setContent {
            MGOManagerTheme {
                AccountListCard(
                    account = testAccountWithSus,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Sus").assertIsDisplayed()
    }

    @Test
    fun accountListCard_showsChevronIcon() {
        composeTestRule.setContent {
            MGOManagerTheme {
                AccountListCard(
                    account = testAccount1,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Details").assertIsDisplayed()
    }

    @Test
    fun accountListCard_onClick_callsCallback() {
        var clicked = false
        composeTestRule.setContent {
            MGOManagerTheme {
                AccountListCard(
                    account = testAccount1,
                    onClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("MGO_TestAccount1").performClick()

        assert(clicked)
    }

    // ============================================================
    // SortOption Display Tests
    // ============================================================

    @Test
    fun searchAndSortSection_displaysNameSortOption() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SearchAndSortSection(
                    searchQuery = "",
                    sortOption = SortOption.NAME,
                    sortDirection = SortDirection.ASC,
                    onSearchQueryChange = {},
                    onSortOptionChange = {},
                    onToggleSortDirection = {},
                    sortDropdownExpanded = false,
                    onSortDropdownExpandedChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Sortiere nach: Name", substring = true).assertIsDisplayed()
    }

    @Test
    fun searchAndSortSection_displaysASCSortDirection() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SearchAndSortSection(
                    searchQuery = "",
                    sortOption = SortOption.NAME,
                    sortDirection = SortDirection.ASC,
                    onSearchQueryChange = {},
                    onSortOptionChange = {},
                    onToggleSortDirection = {},
                    sortDropdownExpanded = false,
                    onSortDropdownExpandedChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("ASC").assertIsDisplayed()
    }

    @Test
    fun searchAndSortSection_toggleDirection_callsCallback() {
        var toggled = false
        composeTestRule.setContent {
            MGOManagerTheme {
                SearchAndSortSection(
                    searchQuery = "",
                    sortOption = SortOption.NAME,
                    sortDirection = SortDirection.ASC,
                    onSearchQueryChange = {},
                    onSortOptionChange = {},
                    onToggleSortDirection = { toggled = true },
                    sortDropdownExpanded = false,
                    onSortDropdownExpandedChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("ASC").performClick()

        assert(toggled)
    }
}
