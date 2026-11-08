package app.babylog.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.babylog.data.repo.EventDraft
import app.babylog.data.sync.ManualSyncOutcome
import app.babylog.data.sync.SyncState
import app.babylog.di.AppContainer
import app.babylog.domain.ChildSelection
import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderKey
import app.babylog.ui.reminders.ReminderSheet
import app.babylog.domain.displayName
import app.babylog.domain.BabyEvent
import app.babylog.domain.EventType
import app.babylog.domain.badgesFor
import app.babylog.ui.common.rememberNow
import app.babylog.ui.growth.GrowthViewModel
import app.babylog.ui.trends.TrendsScreen
import app.babylog.ui.trends.TrendsViewModel
import app.babylog.ui.home.ActivityScreen
import app.babylog.ui.home.BottleSheet
import app.babylog.ui.home.DiaperSheet
import app.babylog.ui.home.EventListOverlay
import app.babylog.ui.home.GrowthSheet
import app.babylog.ui.home.HealthSheet
import app.babylog.ui.home.HomeViewModel
import app.babylog.ui.home.ReminderAction
import app.babylog.ui.home.SheetTarget
import app.babylog.ui.home.SolidsSheet
import app.babylog.ui.home.VaccineSheet
import app.babylog.ui.home.lastDoses as lastDosesFor
import app.babylog.ui.history.HistoryScreen
import app.babylog.ui.history.HistoryViewModel
import app.babylog.ui.settings.SettingsNav
import app.babylog.ui.settings.SettingsScreen
import app.babylog.ui.settings.SettingsSection
import app.babylog.ui.settings.SettingsViewModel
import app.babylog.BuildConfig
import app.babylog.ui.common.rememberHaptics
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Spacing
import app.babylog.ui.theme.accentFor
import androidx.compose.runtime.collectAsState
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

enum class Destination(val label: String, val icon: ImageVector) {
    ACTIVITY("Activity", Icons.Filled.Home),
    HISTORY("Summary", Icons.Filled.DateRange),
    TRENDS("Trends", Icons.Filled.List),
    SETTINGS("Settings", Icons.Filled.List),
}

/**
 * The shell. Two things here are deliberate fixes for bugs the owner hit in the reference app, not styling choices:
 *
 *  1. **Insets.** The `NavigationBar` owns the bottom system inset itself, so the Scaffold's
 *     `contentWindowInsets` excludes the bottom — otherwise the inset is applied twice and content is
 *     pushed off-screen. The content padding then carries the bar height plus a gap, so the last card
 *     scrolls clear of everything. the reference app clips its Child screen here.
 *  2. **Back.** System back returns to Activity from any tab or Settings instead of leaving the app.
 *     the reference app's back exits, which the owner reported unprompted.
 */
@Composable
fun AppScaffold(container: AppContainer) {
    var destination by rememberSaveable { mutableStateOf(Destination.ACTIVITY) }
    // Which Settings submenu is open, or null for the category menu. Owned here rather than inside the
    // screen because the shell has to answer two questions about it: what a back press does, and which
    // list the top scrim follows.
    var settingsSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    val settingsNav = SettingsNav(settingsSection)
    val factory = remember {
        viewModelFactory {
            initializer {
                HomeViewModel(container.events, container.children, container.settings, container.foods)
            }
        }
    }
    val home: HomeViewModel = viewModel(factory = factory)
    val state by home.state.collectAsState()
    val openSheet by home.openSheet.collectAsState()
    val openEntries by home.openEntries.collectAsState()
    val editing by home.editing.collectAsState()
    val foods by home.foodList.collectAsState()

    // One snackbar for the whole app, owned here because the overlay it is raised from is drawn over
    // the Scaffold: a host inside the Scaffold's content would sit underneath it.
    val snackbarHostState = remember { SnackbarHostState() }

    // History observes the SAME stored child selection as Activity (via the shared switcher), so
    // "who am I looking at" cannot mean two different things on two tabs.
    val historyFactory = remember {
        viewModelFactory {
            initializer { HistoryViewModel(container.events, container.children, container.settings) }
        }
    }
    val history: HistoryViewModel = viewModel(factory = historyFactory)
    val historyState by history.state.collectAsState()
    val historyHidden by history.hidden.collectAsState()

    // The growth reference is loaded only when something needs it - the chart screen, or the Settings
    // row that names the dataset. Parsing it at launch would cost every session a stall for a screen
    // most sessions never open.
    val growthFactory = remember {
        viewModelFactory {
            initializer {
                GrowthViewModel(
                    events = container.events,
                    children = container.children,
                    settings = container.settings,
                    reference = container::growth,
                    datasetRepository = container.growthDatasetRepository,
                )
            }
        }
    }
    val growthVm: GrowthViewModel = viewModel(factory = growthFactory)
    val growthState by growthVm.state.collectAsState()

    // Trends' own state: which activity and which window. It observes the SAME stored child selection as
    // Activity, Summary and Growth, so a chart cannot chart somebody the switcher is not showing.
    val trendsFactory = remember {
        viewModelFactory {
            initializer {
                TrendsViewModel(container.events, container.children, container.settings)
            }
        }
    }
    val trendsVm: TrendsViewModel = viewModel(factory = trendsFactory)
    val trendsState by trendsVm.state.collectAsState()

    val settingsFactory = remember {
        viewModelFactory {
            initializer {
                SettingsViewModel(
                    settings = container.settings,
                    children = container.children,
                    maintenance = container.maintenance,
                    sync = container.sync,
                    growthDataset = container.growthDatasetRepository,
                    // The reminder engine lives in the container; the reset clears rules and then asks it
                    // to cancel the alarms they owned, which is the same pass that keeps a disabled rule silent.
                    refreshReminders = { container.refreshReminders() },
                    updates = container.updates,
                )
            }
        }
    }
    val settingsVm: SettingsViewModel = viewModel(factory = settingsFactory)
    val settingsChildren by settingsVm.childrenList.collectAsState()
    val updateStatus by settingsVm.updateStatus.collectAsState()
    val remindersUi by home.remindersUi.collectAsState()
    val reminderConfigs by home.reminderConfigs.collectAsState()
    val notificationsEnabled by home.notificationsEnabled.collectAsState()
    // Which activity's reminder sheet is open, if any. Its own state rather than a ViewModel flow: it is a sheet,
    // and every other sheet in this app is hosted the same way.
    // Which activity's reminder sheet is open, and for which child: a chip knows its own child, and with both
    // children on screen the second chip must open the second twin's rule.
    var reminderSheet by remember { mutableStateOf<ReminderTarget?>(null) }

    // Arm the reminders on the first frame. Not in Application.onCreate: the container is lazy so a process
    // started for a broadcast does not open the database and preferences unless it needs them, and a boot
    // receiver re-arms on its own path.
    LaunchedEffect(Unit) { container.refreshReminders() }

    // A reminder's tap: switch to Activity and open that activity's sheet FOR THAT CHILD. Collected here rather
    // than read once in the activity, because the tap can arrive while the app is already on screen.
    val pendingSheet by container.pendingSheetRequest.collectAsState()
    LaunchedEffect(pendingSheet) {
        pendingSheet?.let { request ->
            destination = Destination.ACTIVITY
            home.openSheetFor(request.type, request.childId)
            container.pendingSheetRequest.value = null
        }
    }
    val settingsUnits by settingsVm.units.collectAsState()
    val settingsTheme by settingsVm.theme.collectAsState()
    val settingsNight by settingsVm.nightWindow.collectAsState()
    val settingsPresets by settingsVm.bottlePresets.collectAsState()
    val medicationPresets by settingsVm.medicationPresets.collectAsState()
    val hiddenActivities by settingsVm.hiddenActivities.collectAsState()
    val activitiesWithReminders by settingsVm.activitiesWithReminders.collectAsState()
    val syncState by settingsVm.syncState.collectAsState()
    val syncMessage by settingsVm.syncMessage.collectAsState()
    val pendingWrites by settingsVm.pendingWrites.collectAsState()
    val manualSyncInProgress by settingsVm.manualSyncInProgress.collectAsState()

    // The bundled medicine names, read once from the asset. Small (~2 KB) and only needed by the health
    // sheet, but parsed up front so the first tap on Medication is not the moment a file read happens.
    val medicationNames by produceState(initialValue = emptyList<String>()) {
        value = container.medications()
    }
    val settingsMessage by settingsVm.message.collectAsState()
    val growthUpdateMessage by settingsVm.growthUpdateMessage.collectAsState()

    LaunchedEffect(Unit) {
        container.growthDatasetRepository.checkIfDue()
    }

    // Which growth reference produced a percentile is shown in Settings too. Loaded on demand rather
    // than at launch, so the ~490 KB parse is paid only by whoever opens the screen that displays it,
    // and OBSERVED rather than polled, so an accepted update changes this line on the screen that
    // triggered it. Found on the phone in Phase 13.5: the line used to keep reading `v2026.10` beside
    // `Updated to v2026.11` until the parent left Settings and came back.
    val growthIdentity by container.growthDatasetRepository.identityLine.collectAsState()
    LaunchedEffect(destination) {
        if (destination == Destination.SETTINGS) {
            container.growth()
        } else {
            // Settings reopens at its menu, never at whichever submenu was last open. A submenu is only
            // reachable from the menu, so restoring one reads as a screen with no way to its siblings.
            settingsSection = null
        }
    }

    // Backup files go through the Storage Access Framework: no permission, no FileProvider, and the
    // user chooses the destination, so the app never owns the file.
    val context = LocalContext.current

    // The notification permission. Requested from the reminder sheet, not on launch: a permission dialog before the
    // parent has asked for a reminder is the wrong order, and denying it must leave a working app.
    //
    // The ViewModel cannot ask this itself (NotificationManagerCompat needs a Context), so the two answers are
    // handed to it here, where the Context already is.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { home.refreshNotificationState() }
    val onRequestNotifications: () -> Unit = {
        val granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Already allowed, or the OS will no longer show the dialog: the system screen is the only place left
            // that can change this.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        home.refreshNotificationState()
    }
    LaunchedEffect(Unit) {
        home.checkNotificationsEnabled = {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        // The alarm side of a rule change lives on the container, so the ViewModel is given the one line that
        // touches it rather than a Context.
        home.onRemindersChanged = { container.refreshReminders() }
        home.refreshNotificationState()
    }

    // Google Sign-In, as the SECOND route into the same two accounts. It needs an OAuth web client id
    // and a registered signing-certificate SHA-1, which is why email/password is the primary one: a
    // release build will only accept Google sign-in once Phase 14 registers the release key's SHA-1.
    @Suppress("DEPRECATION")
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val intent = result.data
        if (intent == null) {
            settingsVm.showMessage("Google sign-in was cancelled.")
        } else {
            val account = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val token = runCatching { account.result?.idToken }.getOrNull()
            if (token.isNullOrBlank()) {
                settingsVm.showMessage("Google sign-in did not return a token. Try again.")
            } else {
                settingsVm.signInWithGoogle(token)
            }
        }
    }

    @Suppress("DEPRECATION")
    fun launchGoogleSignIn() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.FIREBASE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
    }
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<String?>(null) }
    // A pending delete is confirmed before anything is written: a soft delete is recoverable, but only
    // for as long as the snackbar is on screen.
    var pendingDelete by remember { mutableStateOf<BabyEvent?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = settingsVm.exportJson()
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    }.isSuccess
                }
                settingsVm.showMessage(if (written) "Backup exported." else "Could not write that file.")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (text == null) settingsVm.showMessage("Could not read that file.") else pendingImport = text
            }
        }
    }

    val colors = BabyLogTheme.colors
    // The system navigation bar's height, needed twice: Settings' own bottom padding, and the snackbar,
    // which has to clear both it and the app's own navigation bar.
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Owned here, not inside ActivityScreen, so this shell can react to scrolling. The insets are
    // applied as LazyColumn contentPadding, which means items scroll THROUGH the status-bar region -
    // without the scrim below, a scrolled row collides with the clock and battery icons.
    // Each destination owns its own scroll position, but the shell draws ONE scrim — so the fade must
    // follow whichever list is actually on screen. Deriving it from Activity's list alone (as this did)
    // meant Settings and History never faded at all, and their content collided with the clock and
    // battery icons while scrolling.
    val activityListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    // Settings owns two lists: the category menu's and the open submenu's. They are separate because
    // sharing one opens every submenu at the menu's offset and returns the menu at the submenu's.
    val settingsMenuListState = rememberLazyListState()
    // Keyed by section, so each submenu starts at its own top rather than where the last one stopped.
    val settingsSectionListState = key(settingsSection) { rememberLazyListState() }
    // Trends scrolls its own list now that it holds a chart pane, so it fades like the others.
    val trendsListState = rememberLazyListState()
    val settingsListState =
        if (settingsSection == null) settingsMenuListState else settingsSectionListState
    val activeListState = when (destination) {
        Destination.ACTIVITY -> activityListState
        Destination.HISTORY -> historyListState
        Destination.SETTINGS -> settingsListState
        Destination.TRENDS -> trendsListState
    }
    val scrolled by remember(activeListState) {
        derivedStateOf {
            activeListState?.let {
                it.firstVisibleItemIndex > 0 || it.firstVisibleItemScrollOffset > 0
            } ?: false
        }
    }
    val scrimAlpha by animateFloatAsState(if (scrolled) 1f else 0f, label = "topScrim")

    BackHandler(enabled = destination != Destination.ACTIVITY) {
        // A Settings submenu consumes the first back press and lands on the menu; only the menu's own
        // back leaves Settings for Activity. `SettingsNav` owns that rule, so the screen, the handler
        // and the test all read the same one.
        val afterBack = settingsNav.back()
        if (afterBack == null) {
            destination = Destination.ACTIVITY
        } else {
            settingsSection = afterBack.section
        }
    }

    // The whole shell sits inside a Box so the logging sheet can be drawn, as a SIBLING of the
    // Scaffold, over the bottom nav. A sheet rendered inside the Scaffold's content would sit under
    // the navigation bar, which is exactly the clipping bug this app exists to avoid.
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            if (destination != Destination.SETTINGS) {
                Column {
                    // A divider so the bar reads as a solid surface rather than blending into the page.
                    HorizontalDivider(color = colors.divider)
                    NavigationBar(containerColor = colors.surface) {
                        Destination.entries
                            .filter { it != Destination.SETTINGS }
                            .forEach { tab ->
                                NavItem(
                                    tab = tab,
                                    selected = destination == tab,
                                    onSelect = { destination = it },
                                )
                            }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    ) { inner ->
        val layoutDirection = LocalLayoutDirection.current
        // Settings has no bottom bar of its own, so the Scaffold does not consume the bottom inset for
        // it. That inset is handed to SettingsScreen to apply OUTSIDE its scroll: inside, it scrolls
        // away with the content and rows pass under the transparent system navigation bar.
        val settingsBottomInset = if (destination == Destination.SETTINGS) navBarInset else 0.dp
        val padding = PaddingValues(
            start = inner.calculateStartPadding(layoutDirection) + Spacing.gutter,
            end = inner.calculateEndPadding(layoutDirection) + Spacing.gutter,
            top = inner.calculateTopPadding(),
            bottom = inner.calculateBottomPadding() + Spacing.cardGap,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (destination) {
                Destination.ACTIVITY -> ActivityScreen(
                    state = state,
                    syncRefreshing = manualSyncInProgress,
                    onSyncNow = {
                        scope.launch {
                            val outcome = settingsVm.syncManually()
                            val result = when (outcome) {
                                ManualSyncOutcome.SYNCED ->
                                    settingsVm.syncMessage.value ?: "Up to date."
                                ManualSyncOutcome.COOLDOWN ->
                                    "Please wait before syncing again."
                                ManualSyncOutcome.ALREADY_RUNNING ->
                                    "Sync is already running."
                                ManualSyncOutcome.NOT_SIGNED_IN -> when (syncState) {
                                    SyncState.NotConfigured ->
                                        "Sync isn't set up in this build."
                                    is SyncState.Refused ->
                                        "Sign in with one of the two family accounts to sync."
                                    else -> "Sign in from Settings to sync across devices."
                                }
                            }
                            snackbarHostState.showSnackbar(result)
                        }
                    },
                    onSelectChild = home::select,
                    onOpenSheet = home::openSheet,
                    onOpenVaccine = home::openVaccineRecord,
                    onQuickDiaper = home::logDiaper,
                    onQuickBottle = home::logBottle,
                    onStartSleep = home::startSleep,
                    onStop = home::stopSleep,
                    onOpenSettings = { destination = Destination.SETTINGS },
                    // The Upcoming card names the child in its row, so it opens that child's rule too.
                    onOpenReminder = { type, childId -> reminderSheet = ReminderTarget(type, childId) },
                    onSeeAll = home::openEntries,
                    upcoming = remindersUi.upcoming,
                    reminderChips = remindersUi.chips,
                    onReminderAction = { type, action, childId ->
                        when (action) {
                            ReminderAction.SET -> reminderSheet = ReminderTarget(type, childId)
                            ReminderAction.REMOVE -> {
                                // Remove writes a disabled rule rather than deleting the key: "not set" and "set but
                                // off" are the same thing to the parent, and a disabled rule keeps the interval for
                                // next time. With no child named (the menu on a Both card) it clears every child in
                                // scope, because that is what "remove this card's reminder" means when both are shown.
                                val targets = childId?.let(::listOf) ?: state.childIds.ifEmpty {
                                    listOfNotNull(settingsChildren.firstOrNull()?.id)
                                }
                                targets.forEach { home.removeReminder(type, it) }
                                scope.launch {
                                    snackbarHostState.showSnackbar("${type.displayName()} reminder removed")
                                }
                            }
                        }
                    },
                    onEditEntry = home::editEntry,
                    onDeleteEntry = { pendingDelete = it },
                    contentPadding = padding,
                    listState = activityListState,
                )
                Destination.HISTORY -> HistoryScreen(
                    state = historyState,
                    hidden = historyHidden,
                    onSelectRange = history::setRange,
                    onToggleFilter = history::toggleFilter,
                    onShowAll = history::showAll,
                    onShowNone = history::showNone,
                    onSelectChild = home::select,
                    onOpenSettings = { destination = Destination.SETTINGS },
                    contentPadding = padding,
                    listState = historyListState,
                )
                Destination.TRENDS -> TrendsScreen(
                    growth = growthState,
                    onSelectMetric = growthVm::selectMetric,
                    onEnsureLoaded = growthVm::ensureLoaded,
                    growthUpdateMessage = growthVm.updateMessage.collectAsState().value,
                    onCheckGrowthUpdates = growthVm::checkForUpdates,
                    trends = trendsState,
                    onSelectRange = trendsVm::selectRange,
                    onSelectType = trendsVm::selectType,
                    children = state.children,
                    selection = state.selection,
                    onSelectChild = home::select,
                    onOpenSettings = { destination = Destination.SETTINGS },
                    contentPadding = padding,
                    listState = trendsListState,
                )
                Destination.SETTINGS -> SettingsScreen(
                    section = settingsSection,
                    onOpenSection = { entry -> settingsSection = settingsNav.open(entry).section },
                    onBackToMenu = { settingsSection = null },
                    children = settingsChildren,
                    units = settingsUnits,
                    nightWindow = settingsNight,
                    theme = settingsTheme,
                    presets = settingsPresets,
                    medicationPresets = medicationPresets,
                    hiddenActivities = hiddenActivities,
                    activitiesWithReminders = activitiesWithReminders,
                    onSetActivityHidden = settingsVm::setActivityHidden,
                    growthReference = growthIdentity,
                    growthUpdateMessage = growthUpdateMessage,
                    onCheckGrowthUpdates = settingsVm::checkGrowthUpdates,
                    updateStatus = updateStatus,
                    onSetUpdateCheck = settingsVm::setUpdateCheckEnabled,
                    onCheckUpdatesNow = settingsVm::checkForUpdatesNow,
                    onTheme = settingsVm::setTheme,
                    onUnitSystem = settingsVm::setUnitSystem,
                    onLengthUnit = settingsVm::setLengthUnit,
                    onWeightUnit = settingsVm::setWeightUnit,
                    onVolumeUnit = settingsVm::setVolumeUnit,
                    onTemperatureUnit = settingsVm::setTemperatureUnit,
                    onTimeFormat = settingsVm::setTimeFormat,
                    onNightStart = settingsVm::setNightStartHour,
                    onNightEnd = settingsVm::setNightEndHour,
                    onAddPreset = settingsVm::addBottlePreset,
                    onRemovePreset = settingsVm::removeBottlePreset,
                    onAddMedicationPreset = settingsVm::addMedicationPreset,
                    onRemoveMedicationPreset = settingsVm::removeMedicationPreset,
                    onUpdateChild = settingsVm::updateChild,
                    onAddChild = settingsVm::addChild,
                    onArchiveChild = settingsVm::archiveChild,
                    onDeleteChildPermanently = settingsVm::deleteChildPermanently,
                    onExport = { exportLauncher.launch(backupFileName()) },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onResetLocal = settingsVm::resetLocal,
                    onDeleteServerData = settingsVm::deleteAllServerData,
                    message = settingsMessage,
                    syncState = syncState,
                    syncMessage = syncMessage,
                    pendingWrites = pendingWrites,
                    googleSignInAvailable = settingsVm.googleSignInAvailable,
                    onSignIn = settingsVm::signIn,
                    onSignInWithGoogle = { launchGoogleSignIn() },
                    onSignOut = settingsVm::signOut,
                    onSyncNow = settingsVm::syncNow,
                    contentPadding = padding,
                    listState = settingsListState,
                    bottomInset = settingsBottomInset,
                )
            }

            // Opaque behind the status bar once the list has moved, so the clock and battery never
            // sit on top of log text. Solid across the bar itself, then faded out below it, so there
            // is no hard seam. Drawn last, so it covers content rather than being covered.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp)
                    .alpha(scrimAlpha)
                    .background(
                        Brush.verticalGradient(
                            0.0f to colors.background,
                            0.65f to colors.background,
                            1.0f to colors.background.copy(alpha = 0f),
                        ),
                    ),
            )
        }
    }

        // The reminder sheet. Hosted here rather than inside the Settings destination so it covers the whole
        // screen including the bottom bar, exactly like a logging sheet - and because the sheet host below is
        // the one place that already knows how to stack a full-screen editor over anything.
        // The reminder sheet, opened from a card's `⋮`. Per child only: when two children are on screen the
        // sheet asks which one, defaulting to the single selected child when the switcher names one.
        reminderSheet?.let { target ->
            val type = target.type
            key(type, target.childId) {
                val kids = settingsChildren
                // The chip's own child wins; otherwise the switcher's single child; otherwise the first.
                val defaultChildId = target.childId
                    ?: state.childIds.singleOrNull()
                    ?: kids.firstOrNull()?.id
                // Every child's rule, so the sheet can follow the child it is editing. `editing` inside the sheet is
                // about whether a reminder is LIVE, not whether a row exists: removing one writes a disabled rule so
                // the interval is remembered.
                ReminderSheet(
                    type = type,
                    accent = colors.accentFor(type),
                    children = kids,
                    initialChildId = defaultChildId,
                    zone = ZoneId.systemDefault(),
                    configs = kids.mapNotNull { child ->
                        reminderConfigs[ReminderKey(type, child.id)]?.let { child.id to it }
                    }.toMap(),
                    notificationsEnabled = notificationsEnabled,
                    nightStartHour = settingsNight.startHour,
                    nightEndHour = settingsNight.endHour,
                    onDismiss = { reminderSheet = null },
                    onSave = { childId, config ->
                        home.saveReminder(type, childId, config)
                        reminderSheet = null
                        scope.launch {
                            snackbarHostState.showSnackbar("${type.displayName()} reminder added")
                        }
                    },
                    // The same path the card's `⋮` menu uses, so a rule removed from the sheet and one removed
                    // from the menu cannot do different things.
                    onRemove = { childId ->
                        home.removeReminder(type, childId)
                        reminderSheet = null
                        scope.launch {
                            snackbarHostState.showSnackbar("${type.displayName()} reminder removed")
                        }
                    },
                    onAllowNotifications = onRequestNotifications,
                )
            }
        }

        // The percentile chart lives in Trends' Growth tab now, so there is no Growth overlay to host here.

        // "See all": every entry of one activity for the current child/both selection. Drawn before the
        // sheets so the edit sheet it launches stacks on top of it, and as a sibling of the Scaffold so
        // it covers the bottom navigation bar rather than being clipped by it.
        val listType = openEntries
        if (listType != null) {
            // The clock ticks only while the list is open: its day headers ("Today"/"Yesterday") are all
            // it feeds, so there is no reason to recompose the shell for it the rest of the time.
            val listNow = rememberNow(anyRunning = false)
            EventListOverlay(
                type = listType,
                entries = state.entriesOf(listType),
                units = state.units,
                foodNames = state.foodNames,
                childLabel = state.childLabel,
                childNames = state.children.associate { it.id to it.name },
                showChildBadge = state.childIds.size > 1,
                zone = remember { ZoneId.systemDefault() },
                now = listNow,
                onDismiss = home::dismissEntries,
                onEditEntry = home::editEntry,
                onDeleteEntry = { pendingDelete = it },
            )
        }

        // One sheet, two jobs: logging a new event (`openSheet`) or editing a row (`editing`, which wins,
        // because tapping a row while the list is open means that row). Keyed on the target so tapping a
        // SECOND row re-seeds the fields instead of showing the previous row's values.
        //
        // Health has two forms, so the target is a SheetTarget rather than a bare EventType: a new record
        // uses the button that was tapped, and an EDIT decides from the row's own content — a row holding a
        // vaccine and nothing else is a vaccine record, which is the only shape this app can write.
        val editingEvent = editing
        val target = editingEvent?.let {
            SheetTarget(
                type = it.type,
                vaccine = it.vaccineName != null && it.temperatureC == null && it.medication == null,
            )
        } ?: openSheet
        if (target != null) {
            val sheetType = target.type
            // `target.vaccine` is part of the key: without it, going from the health form to the vaccine
            // form on the same HEALTH type would keep the previous form's remembered field values.
            key(sheetType, editingEvent?.id, target.vaccine) {
                val sheetNow = remember(sheetType) { System.currentTimeMillis() }
                val zone = remember { ZoneId.systemDefault() }
                val accent = colors.accentFor(sheetType)
                // Editing says who the row belongs to, which in Both mode is not the switcher's answer; a
                // sheet opened from a reminder says who the REMINDER was about, which is a third answer.
                val targetChildId = editingEvent?.childId ?: target.childId
                val sheetChildLabel = targetChildId?.let(state::childName) ?: state.childLabel
                val sheetChildIds = targetChildId?.let { listOf(it) } ?: state.childIds
                val onSaveSheet: (EventDraft) -> Unit = { draft ->
                    if (editingEvent != null) home.saveEdit(editingEvent, draft) else home.save(draft, sheetChildIds)
                }
                val onDismissSheet: () -> Unit = {
                    if (editingEvent != null) home.dismissEdit() else home.dismissSheet()
                }
                // Delete from inside the sheet: close it, then run the same confirm + Undo path a long-press uses,
                // so there is one delete story in the app rather than two.
                val onDeleteEntry: (() -> Unit)? = editingEvent?.let { row ->
                    {
                        home.dismissEdit()
                        pendingDelete = row
                    }
                }
                when (sheetType) {
                    EventType.BOTTLE -> BottleSheet(
                        accent = accent,
                        childLabel = sheetChildLabel,
                        units = state.units,
                        presets = settingsPresets,
                        zone = zone,
                        now = sheetNow,
                        initialAmountMl = state.lastBottlePouredMl,
                        onDismiss = onDismissSheet,
                        onSave = onSaveSheet,
                        initial = editingEvent,
                        onDelete = onDeleteEntry,
                    )
                    EventType.DIAPER -> DiaperSheet(
                        accent = accent,
                        childLabel = sheetChildLabel,
                        zone = zone,
                        now = sheetNow,
                        onDismiss = onDismissSheet,
                        onSave = onSaveSheet,
                        initial = editingEvent,
                        onDelete = onDeleteEntry,
                    )
                    EventType.SOLIDS -> SolidsSheet(
                        accent = accent,
                        childLabel = sheetChildLabel,
                        zone = zone,
                        now = sheetNow,
                        foods = foods,
                        onAddFood = home::addCustomFood,
                        onRenameFood = home::renameFood,
                        onHideFood = home::setFoodHidden,
                        onDeleteFood = home::deleteFood,
                        onDismiss = onDismissSheet,
                        onSave = onSaveSheet,
                        initial = editingEvent,
                        onDelete = onDeleteEntry,
                    )
                    EventType.GROWTH -> GrowthSheet(
                        childLabel = sheetChildLabel,
                        units = state.units,
                        zone = zone,
                        now = sheetNow,
                        onDismiss = onDismissSheet,
                        onSave = onSaveSheet,
                        initial = editingEvent,
                        onDelete = onDeleteEntry,
                    )
                    EventType.HEALTH -> if (target.vaccine) VaccineSheet(
                        childLabel = sheetChildLabel,
                        zone = zone,
                        now = sheetNow,
                        onDismiss = onDismissSheet,
                        onSave = onSaveSheet,
                        initial = editingEvent,
                        onDelete = onDeleteEntry,
                    ) else HealthSheet(
                        childLabel = sheetChildLabel,
                        medications = medicationNames,
                        medicationPresets = medicationPresets,
                        // The hint is the parent's OWN last dose for this medicine, named per child, and it
                        // is only ever shown — the dose field stays empty until they type it.
                        lastDoses = { name ->
                            lastDosesFor(state.entriesOf(EventType.HEALTH), state.childIds, name)
                                .map { (childId, dose) -> state.childName(childId) to dose }
                        },
                        zone = zone,
                        now = sheetNow,
                        onDismiss = onDismissSheet,
                        onSave = onSaveSheet,
                        initial = editingEvent,
                        onDelete = onDeleteEntry,
                    )
                    // Named rather than swallowed by an `else`, and this `when` has NO else on purpose: a new
                    // EventType must state what its sheet is, or the build fails. The bug this replaces was a
                    // private "types with a sheet" set that went stale, so Growth's button looked live and
                    // did nothing. Sleep runs from the card as a timer; Health now names BOTH of its forms
                    // above rather than being grouped with Sleep here.
                    EventType.SLEEP -> Unit
                }
            }
        }

        // Import confirmation. Replace-everything is destructive, so it says so, and it offers to
        // export first rather than assuming the current state is disposable.
        pendingImport?.let { fileText ->
            AlertDialog(
                onDismissRequest = { pendingImport = null },
                title = { Text("Replace everything?") },
                text = {
                    Text(
                        "Importing replaces every child, event and food with the contents of that file. " +
                            "Anything logged since that backup is lost.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingImport = null
                        scope.launch {
                            runCatching { settingsVm.importJson(fileText) }
                                .onFailure {
                                    settingsVm.showMessage(it.message ?: "That file could not be imported.")
                                }
                        }
                    }) { Text("Replace") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            pendingImport = null
                            exportLauncher.launch(backupFileName())
                        }) { Text("Export first") }
                        TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
                    }
                },
            )
        }

        // Deleting an entry is confirmed, then offers an Undo for as long as the snackbar is up: a
        // mis-tap at 3am should cost one tap to fix, not a re-typed feed.
        pendingDelete?.let { event ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete this ${event.type.displayName().lowercase()}?") },
                text = {
                    Text(
                        "It will disappear from the cards and from Summary. " +
                            "You can undo it from the next message.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        scope.launch {
                            val tombstone = home.deleteEntry(event)
                            val result = snackbarHostState.showSnackbar(
                                message = "${event.type.displayName()} deleted",
                                actionLabel = "Undo",
                                // Long (10s), not Short (4s). An action snackbar is a deadline, and 4 seconds
                                // is not enough to notice the delete, decide, and reach the button - found on
                                // device, where a Short snackbar expired between a UI dump and the tap and the
                                // tap landed on the Diaper card underneath. Material's own guidance is Long
                                // whenever a snackbar carries an action.
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) home.undo(tombstone)
                        }
                    }) { Text("Delete", color = colors.danger) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                },
            )
        }

        // Drawn LAST, so it is visible above the Scaffold AND above the "See all" overlay, which is a
        // sibling of the Scaffold rather than content inside it. Raised clear of the app's own bottom
        // bar, which covers the bottom of the screen on every destination that shows one.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.bottomBarHeight + navBarInset + Spacing.cardGap),
        )
    }
}

/** A dated, obviously-a-backup file name for the create-document picker. */
private fun backupFileName(): String = "babylog-backup-${LocalDate.now()}.json"

@Composable
private fun RowScope.NavItem(
    tab: Destination,
    selected: Boolean,
    onSelect: (Destination) -> Unit,
) {
    val colors = BabyLogTheme.colors
    // Switching tab is a deliberate act and gets the ordinary tap; re-tapping the tab already on screen is
    // not a switch, so it stays silent rather than buzzing at a no-op.
    val haptics = rememberHaptics()
    NavigationBarItem(
        selected = selected,
        onClick = {
            if (!selected) haptics.tap()
            onSelect(tab)
        },
        icon = { Icon(tab.icon, contentDescription = null) },
        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = colors.navActive,
            selectedTextColor = colors.navActive,
            unselectedIconColor = colors.navInactive,
            unselectedTextColor = colors.navInactive,
            indicatorColor = colors.surfaceRaised,
        ),
    )
}

/** A reminder sheet request: which activity, and which child's rule to open. */
private data class ReminderTarget(val type: EventType, val childId: String?)
