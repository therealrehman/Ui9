package com.oklabs.oneui9launcher

import android.content.Context

/**
 * Step 13: the policies as text inside the app, so they can be read without internet (the app has no internet permission).
 * They say the same as the pages in docs/ (privacy-policy.html, terms.html). When you change one, change the other.
 * {app}, {company} and {email} are replaced with the app name, the developer name and the contact address from strings.xml.
 */
object LegalText {

    const val EFFECTIVE = "October 20, 2026"

    class Section(val heading: String, val paragraphs: List<String>)

    fun fill(context: Context, text: String): String =
        text.replace("{app}", context.getString(R.string.app_name))
            .replace("{company}", context.getString(R.string.developer_name))
            .replace("{email}", context.getString(R.string.contact_email))

    val privacy: List<Section> = listOf(
        Section(
            "In short",
            listOf(
                "{app} does not collect, store on any server, share or sell your personal data.",
                "The app has no account, no analytics, no advertising and no internet permission. Everything it knows stays on your phone."
            )
        ),
        Section(
            "1. Who we are",
            listOf(
                "{app} (“the app”) is an Android home-screen launcher made by {company} (“we”, “us”).",
                "Contact: {email}"
            )
        ),
        Section(
            "2. Data we collect",
            listOf(
                "None. We do not collect any personal information, device identifiers, location, contacts, messages, photos or usage statistics.",
                "The app does not request the Android INTERNET permission, so it cannot send data off your phone. We run no servers for the app."
            )
        ),
        Section(
            "3. Data kept on your phone",
            listOf(
                "To work, the app saves the following in its private storage on your phone only:",
                "• your Home screen layout (which apps, folders and widgets are on which page, the dock);",
                "• your settings (grid sizes, icon sizes, themes, hidden apps, Edge panel apps, widget style and similar choices);",
                "• a small in-memory cache of app icons, which is cleared when the app closes.",
                "This data never leaves your phone through the app. It is deleted when you uninstall the app or clear its storage in Android settings.",
                "Backups. “Back up layout and settings” writes a text file to a place you choose (for example Downloads or a cloud drive). " +
                    "That file is created and held by you; we never receive it. Separately, Android’s own system backup may include the app’s saved settings " +
                    "in your Google account backup, depending on your phone’s settings. That is handled by Android and Google, not by us."
            )
        ),
        Section(
            "4. Permissions and what they are used for",
            listOf(
                "These are the permissions and special access the app uses, and why (also in Settings > Permissions and data use):"
            )
        ),
        Section(
            "5. Sharing",
            listOf(
                "We do not share, sell or rent any data, because we do not have any. The app contains no advertising or analytics code and no third-party libraries that collect data."
            )
        ),
        Section(
            "6. Contacting us",
            listOf(
                "“Contact us” and “Report a problem” open your own email app with a message to {email}. You see and can change the message before you send it, " +
                    "and nothing is sent unless you press Send. “Report a problem” fills in the app version, the Android version and the phone model " +
                    "to help us find the bug; delete them if you prefer. We use what you send only to answer you and to fix the app."
            )
        ),
        Section(
            "7. Other apps and links",
            listOf(
                "When you open an app from the launcher, or a link such as a policy page, that app or your browser takes over and its own privacy practices apply."
            )
        ),
        Section(
            "8. Children",
            listOf(
                "The app is a general-purpose tool and is not directed at children under 13. Because it collects no personal data, it does not knowingly collect any from children."
            )
        ),
        Section(
            "9. Security",
            listOf(
                "Your layout and settings are kept in the app’s private storage, which other apps cannot read on an unmodified Android phone. Nothing is transmitted by the app."
            )
        ),
        Section(
            "10. Your rights and choices",
            listOf(
                "Because we hold no personal data about you, there is nothing for us to access, correct or delete. You control everything the app stores: " +
                    "clear it in Android Settings > Apps > {app} > Storage, or uninstall the app.",
                "If you have a privacy question or request, email {email}."
            )
        ),
        Section(
            "11. Changes to this policy",
            listOf(
                "If the app changes in a way that affects privacy (for example, if a future version ever adds internet features), we will update this policy and the effective date before the change is released."
            )
        ),
        Section(
            "12. Contact",
            listOf("{company}\nEmail: {email}")
        )
    )

    val terms: List<Section> = listOf(
        Section(
            "Agreement",
            listOf(
                "These terms are an agreement between you and {company} (“we”, “us”) about your use of {app} (“the app”). " +
                    "By installing or using the app you agree to them. If you do not agree, please uninstall the app."
            )
        ),
        Section(
            "1. License",
            listOf(
                "We grant you a personal, non-exclusive, non-transferable, revocable license to install and use the app on Android devices you own or control, for your own non-commercial use."
            )
        ),
        Section(
            "2. What you may not do",
            listOf(
                "• copy, sell, rent or redistribute the app, or publish it under another name;",
                "• reverse engineer or modify the app except where the law allows it despite this restriction;",
                "• use the app to break the law or to harm other people or their devices."
            )
        ),
        Section(
            "3. Your phone and your data",
            listOf(
                "The app changes how your Home screen looks and behaves. It stores your layout and settings on your phone only (see the Privacy policy). " +
                    "You are responsible for keeping your own backups. Widgets are not included in the app’s backup files."
            )
        ),
        Section(
            "4. Optional access you grant",
            listOf(
                "Some features need access you grant in Android settings (notification access for badges, device administrator for double-tap lock). " +
                    "You can withdraw this access at any time. Without it, those features simply stop working."
            )
        ),
        Section(
            "5. Third-party apps and widgets",
            listOf(
                "The app shows and starts apps and widgets made by other companies. We are not responsible for them, and their own terms and privacy policies apply to them."
            )
        ),
        Section(
            "6. No affiliation; trademarks",
            listOf(
                "The app is an independent product of {company}. It is not made, sponsored or endorsed by Samsung or any other phone maker. " +
                    "All code and icons in the app are original. All product names and trademarks mentioned belong to their owners."
            )
        ),
        Section(
            "7. Updates and changes",
            listOf(
                "We may update, change or stop the app at any time. We may also update these terms; the effective date shows the latest version. " +
                    "Continuing to use the app after a change means you accept the new terms."
            )
        ),
        Section(
            "8. Warranty disclaimer",
            listOf(
                "The app is provided “as is” and “as available”, without warranties of any kind, express or implied, including fitness for a particular purpose and uninterrupted or error-free operation. " +
                    "Android phones differ a lot; the app may behave differently or not work on some devices."
            )
        ),
        Section(
            "9. Limitation of liability",
            listOf(
                "To the fullest extent the law allows, {company} is not liable for any indirect, incidental or consequential damages, or for loss of data or settings, arising from your use of the app. " +
                    "Nothing in these terms limits liability that cannot be limited by law."
            )
        ),
        Section(
            "10. Ending",
            listOf(
                "You can stop using the app at any time by uninstalling it. We may end your license if you break these terms."
            )
        ),
        Section(
            "11. Governing law",
            listOf(
                "These terms are governed by the laws of the country in which {company} is established, without regard to conflict-of-law rules, unless your local consumer law says otherwise."
            )
        ),
        Section(
            "12. Contact",
            listOf("{company}\nEmail: {email}")
        )
    )

    /** What each permission / access is for. Shown on Settings > Permissions and data use. */
    class Permission(val name: String, val why: String, val optional: Boolean)

    val permissions: List<Permission> = listOf(
        Permission(
            "Seeing installed apps",
            "To show your apps on the Home screen and the Apps screen, and to find the default Phone, Messages, Browser, Camera and Settings apps. The list is used on the phone only.",
            false
        ),
        Permission(
            "Notification access",
            "Only if you turn on notification badges. The app counts how many notifications each app has to draw a dot or a number on its icon. " +
                "It does not read, store or send the text or content of notifications. You can turn this off at any time in Android Settings.",
            true
        ),
        Permission(
            "Device administrator (lock the screen only)",
            "Only if you turn on “Double-tap to lock”. It lets the app lock your screen when you double-tap an empty spot. " +
                "It cannot wipe data, change passwords or read anything. Switch it off in Settings > Security > Device admin apps.",
            true
        ),
        Permission(
            "Expand status bar",
            "To open the notification panel when you swipe down on the Home screen.",
            false
        ),
        Permission(
            "Request to delete packages",
            "To start Android’s own “Uninstall app?” screen when you choose Uninstall in the quick menu. Android always asks you to confirm.",
            false
        ),
        Permission(
            "Set wallpaper hints",
            "To let the wallpaper slide a little when you swipe between pages.",
            false
        ),
        Permission(
            "Widgets",
            "The app can host widgets from other apps and has its own clock and date widgets. Widgets from other apps are drawn by those apps and follow their own privacy policies.",
            false
        ),
        Permission(
            "Internet",
            "Not requested. The app cannot connect to the internet.",
            false
        )
    )

    val openSource: List<Section> = listOf(
        Section(
            "Code",
            listOf("{app} is written in Kotlin and uses only the Android platform. It has no third-party libraries, no ads and no analytics.")
        ),
        Section(
            "Icons and artwork",
            listOf(
                "Every icon, glyph and picture in the app is drawn in code by {company} from simple shapes. " +
                    "Apps that are not part of the phone’s own system keep their own icons, which belong to their makers."
            )
        ),
        Section(
            "Trademarks",
            listOf(
                "Android is a trademark of Google LLC. Samsung and One UI are trademarks of Samsung Electronics. {app} is not affiliated with, made by, sponsored or endorsed by them."
            )
        )
    )
}
