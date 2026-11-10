package app.babylog.domain

/**
 * Which of the three time inputs the dialog is showing.
 *
 * Three rather than two because the text picker was here first and is the only one that can type `07:45`
 * exactly; the dial and the wheel are the two the owner asked to switch between. The dialog's one button
 * cycles all three, so there is no mode that cannot be reached from any other.
 *
 * It lives in `domain` rather than beside the wheel that draws it because it is now a STORED preference
 * (Phase 29): `SettingsRepository` keeps the mode the dialog should open on, and a repository that typed a
 * UI enum would be a layer pointing the wrong way. The words and the drawable for a mode stay in
 * `ui/home/WheelTimePicker.kt`, where the drawing is.
 */
enum class TimeInputMode { DIAL, WHEEL, TEXT }
