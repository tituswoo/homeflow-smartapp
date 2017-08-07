# Homeflow Installation Instructions
To get started with Homeflow, follow these steps. Setup should take only 5 minutes. 👻 Send an email to [jonathanwhite.dev@gmail.com](mailto:jonathanwhite.dev@gmail.com) or [titus.woo@me.com](mailto:titus.woo@me.com) if you run into any issues.

## Table of Contents

* [Install Homeflow SmartApp](#install-homeflow-smartapp)
  * [Connect Github to SmartThings](#connect-github-to-smartthings)
  * [Enable Github integration](#enable-github-integration)
  * [Add code repository](#add-code-repository)
  * [Install SmartApp](#install-smartapp)
* [Setup OAuth](#setup-oauth)
  * [Enable OAuth](#enable-oauth)
* [Setup Homeflow in the SmartThings app](#setup-homeflow-in-the-smartthings-app)
  * [Add SmartApp](#add-smartapp)
* [Connect your SmartThings account](#connect-your-smartthings-account)
  * [Connect](#connect)

## Install Homeflow SmartApp

### Connect Github to SmartThings
We recommend that you use Github to manage updates and install Homeflow. Github makes getting updates quick and painless.

1. [Create an account on Github](https://github.com/join). Completely free ✨
2. Log into your [SmartThings developer dashboard](https://graph.api.smartthings.com/)

### Enable Github integration
1. Click on “[My SmartApps](https://graph.api.smartthings.com/ide/apps)” 🤓
2. Click on “Enable Github Integration”
3. Follow the next steps to connect your Github account to SmartThings 👾

![Enable Github integration](assets/enable-github-integration.png)

### Add code repository
1. Click on “Settings” ⚙️

![Add Github account](assets/add-github-account.png)

1. Click on “Add new repository”
2. Owner: tituswoo
3. Name: homeflow-smartapp
4. Branch: master
5. Click on “save” 💾

![Github install](assets/github-install.png)

### Install SmartApp
1. Click on “Update from Repo” ⬆️
2. Click on “homeflow-smartapp (master)”

![Update from repo](assets/update-from-repo-1.png)

3. Click on the checkbox beside “smartapps/homeflow/homeflow.src/homeflow.groovy” ✅
4. Click on the checkbox beside “Publish” (don’t forget this step) ✅
5. Click on “Execute Update” 👌

![Update from repo](assets/update-from-repo-2.png)

## Setup OAuth

### Enable OAuth
Enabling OAuth lets you control your smart devices through Homeflow 🕹
1. Click on the edit icon next to your SmartApp

![Edit SmartApp](assets/edit-app.png)

2. From your “Edit SmartApp” page, scroll down and click “Enable OAuth in Smart App”

3. When the OAuth panel expands to reveal Client ID and Client Secret fields, scroll to the bottom of the page and click “Update” ⚡️

![Enable OAuth](assets/update-settings.png)

## Setup Homeflow in the SmartThings app

### Add SmartApp
1. In your SmartThings app, at the bottom, click “Automation”
2. Click on “Add a SmartApp” under the "SmartApps" section
3. Scroll to the bottom of the screen and click “My Apps”
4. Select “homeflow”

![iOS installation 1](assets/ios-installation-1.png)

5. Click and choose the devices you want to control
6. Click on “Next”
7. Enter the code into the Homeflow web app

![iOS installation 2](assets/ios-installation-2.png)

## Connect your SmartThings account

### Connect
1. Enter in the code from your SmartApp to Homeflow

![Edit account code](assets/enter-account-code.png)

## You’re done 🎉
Congrats! You are now setup with Homeflow. Go to your dashboard and start automating your home. 
