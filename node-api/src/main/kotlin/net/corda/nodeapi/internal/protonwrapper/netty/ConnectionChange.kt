/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.protonwrapper.netty

import java.net.InetSocketAddress
import java.security.cert.X509Certificate

data class ConnectionChange(val remoteAddress: InetSocketAddress, val remoteCert: X509Certificate?, val connected: Boolean, val badCert: Boolean)