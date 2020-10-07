# Introduction 
This project contains the source code for the Tapkey Keyring App Template for Android.

# Configurables - "app\app\src\main\res\values\config.xml"

TheTapkey Keyring App Template for Android contains various configurable settings which can be modified according to your setup. This section explains the different configuration options and discusses their respective impacts.

## Sentry

Sentry is a real-time error monitoring tool which allows you to see which errors occur in the field. Once you create a Sentry account you can access the Sentry DSN value, which you need to copy over to the `sentry_dsn` resource. Errors will be logged into the configured project.

## Tapkey Authorization Endpoint

This setting defines the endpoint where the app is able to exchange the Firebase token for the Tapkey token. The URI-value for `tapkey_authorization_endpoint` must be an SSL-secured endpoint ("HTTPS") otherwise the app won't run.

## Tapkey Domain ID

The Tapkey Domain ID is used to separate independent solutions based on the same Tapkey technology. The ID is assigned by Tapkey. Ask Tapkey to get your Domain ID.

## Tapkey OAuth Client ID

The `tapkey_oauth_client_id` defines the ID of the OAuth client that has been created on the self-service registration page.

## Tapkey Identity Provider ID

The `tapkey_identity_provider_id` defines the ID of the identity provider that has been created on the self-service registration page.

## Tapkey Base URI

The `tapkey_base_uri` defines the endpoint to access the Tapkey API. May be changed to access Demo environments.

## ISO7816 AID

The `tk_iso7816_aid` is used as an identifier in NFC connections.

# Firebase

Download and copy the `google-services.json` to configure Firebase (Auth & Analytics) to `app/app/src/debug/google-services.json` and `app/app/src/release/google-services.json`
for debug and release configs.

# Coloring scheme

The Tapkey Keyring App Template for Android is compliant to Material Design Coloring scheme. Both light foreground/dark background and dark foreground/light background (dark mode) configurations are possible.

You can use the Material Design Color Tool (Provided by Google on https://material.io/resources/color/#!) to create a color-scheme free and easy. It is possible to preview the color-scheme and setup Primary and Secondary colors, as well as the text coloring on both.

Once you're done styling your theme you need export the theme. To do so, click on `Export` on the top right and choose Android. A `colors.xml` file will be downloaded.

This file needs to be copied over to `app\app\src\main\res\values\colors.xml`.

Two values need to be added manually for the coloring-scheme to work correctly:

Primary text hint color, modifies the color of the hint on the EditText fields. Recommended values are `#61ffffff` for dark backgrounds and `#84000000` for light backgrounds: `<color name="primaryTextHintColor"></color>`

Green color is used for the green unlocking check-mark. Recommended value is `#99cc00` `<color name="green1_normal"></color>`

It is not necessary to edit the `styles.xml`, all coloring-schemes should be configured correctly.

# Other settings

## Configurable strings

The standard Android file `app\app\src\main\res\values\strings.xml` contains all relevant string values. Values that may/should be changed are:

`app_name` - Name of the app
`tos_url` - URI to open when tapping the terms and conditions string
`address` - Your company's address

## Versioning

Versioning of the app is steered by the `settings.gradle` file. You may setup major, minor and revision codes.

```
1.2.3
^ ^ ^
| | |__________.
| |_____.	   |
|		|      |
Major Minor Revision
```

These settings modify the build.gradle `versionCode` and `versionName` settings. The version name will be displayed on the about screen: "Version 1.2.3"