@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "changemanagement",
        "platform::audit",
        "platform::authorization",
        "platform::event",
        "platform::observability",
        "platform::outbox",
        "platform::workflow"
    })
package ru.ultimavox.itsm.releasemanagement;
