package com.etk2000.ssh_scp.util;

import java.io.IOException;

public interface ScpTransferListener {
	void onComplete();
	
	void onException(IOException e);

	void onProgress(long transferred, long totalSize);
}