@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "servicedesk",
        "changemanagement",
        "problemmanagement",
        "releasemanagement",
        "cmdb",
        "assetmanagement",
        "platform::sla",
        "platform::authorization"
    })
package ru.ultimavox.itsm.reporting;
