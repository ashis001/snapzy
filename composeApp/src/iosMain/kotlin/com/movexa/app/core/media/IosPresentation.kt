package com.movexa.app.core.media

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

internal fun presentFromTopViewController(controller: UIViewController) {
    val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
    var topVC = rootVC
    while (topVC?.presentedViewController != null) {
        topVC = topVC.presentedViewController
    }
    topVC?.presentViewController(controller, animated = true, completion = null)
}
