package net.corda.nodeapi.internal.rpc

import rx.Subscription

class ObservableSubscription(
        val subscription: Subscription
)
