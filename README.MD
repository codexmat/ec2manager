# EC2 Manager Plugin for Jenkins

![Jenkins Plugin](https://img.shields.io/badge/Jenkins-Plugin-brightgreen)
![Build](https://img.shields.io/badge/Build-Passing-green)

## 🔹 Overview
EC2 Manager Plugin helps manage AWS EC2 instances directly from Jenkins.
- **Automatically stop/start EC2 instances** when idle.
- **Reduce AWS costs** by optimizing instance uptime.
- **Integrates with Jenkins** as a node property.

## 🚀 Features
- Stop EC2 instances when **idle for a configured time**.
- Start EC2 instances **when a build starts**.
- Supports **multiple EC2 instances**.

## 🛠 Installation
### **1️⃣ Build the Plugin**
Run:
```sh
mvn clean package -Danimal.sniffer.skip=true -Denforcer.skip=true

Or the command below to rebuild

mvn clean package -Danimal.sniffer.skip=true -Denforcer.skip=true -U