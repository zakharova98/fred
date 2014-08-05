/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.client.async;

import java.io.IOException;
import com.db4o.ObjectContainer;

import freenet.client.ClientMetadata;
import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.keys.ClientKey;
import freenet.keys.ClientKeyBlock;
import freenet.keys.KeyDecodeException;
import freenet.keys.TooBigException;
import freenet.node.LowLevelGetException;
import freenet.node.SendableRequestItem;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;
import freenet.support.api.Bucket;
import freenet.support.io.InsufficientDiskSpaceException;

/** 
 * Fetch a single block file.
 * Used by SplitFileFetcherSegment.
 */
public class SimpleSingleFileFetcher extends BaseSingleFileFetcher implements ClientGetState {

	SimpleSingleFileFetcher(ClientKey key, int maxRetries, FetchContext ctx, ClientRequester parent, 
			GetCompletionCallback rcb, boolean isEssential, boolean dontAdd, long l, ObjectContainer container, ClientContext context, boolean deleteFetchContext, boolean realTimeFlag) {
		super(key, maxRetries, ctx, parent, deleteFetchContext, realTimeFlag);
		this.rcb = rcb;
		this.token = l;
		if(!dontAdd) {
			if(isEssential)
				parent.addMustSucceedBlocks(1);
			else
				parent.addBlock();
			parent.notifyClients(context);
		}
	}

	final GetCompletionCallback rcb;
	final long token;

        private static volatile boolean logMINOR;
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback(){
			@Override
			public void shouldUpdate(){
				logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
			}
		});
	}
	
	// Translate it, then call the real onFailure
	@Override
	public void onFailure(LowLevelGetException e, SendableRequestItem reqTokenIgnored, ClientContext context) {
	    onFailure(translateException(e), false, context);
	}

	// Real onFailure
	protected void onFailure(FetchException e, boolean forceFatal, ClientContext context) {
		if(logMINOR) Logger.minor(this, "onFailure( "+e+" , "+forceFatal+")", e);
		if(parent.isCancelled() || cancelled) {
			if(logMINOR) Logger.minor(this, "Failing: cancelled");
			e = new FetchException(FetchException.CANCELLED);
			forceFatal = true;
		}
		if(!(e.isFatal() || forceFatal) ) {
			if(retry(context)) {
				if(logMINOR) Logger.minor(this, "Retrying");
				return;
			}
		}
		// :(
		unregisterAll(context);
		synchronized(this) {
			finished = true;
		}
		if(e.isFatal() || forceFatal)
			parent.fatallyFailedBlock(context);
		else
			parent.failedBlock(context);
		rcb.onFailure(e, this, context);
	}

	/** Will be overridden by SingleFileFetcher */
	protected void onSuccess(FetchResult data, ClientContext context) {
		if(parent.isCancelled()) {
			data.asBucket().free();
			onFailure(new FetchException(FetchException.CANCELLED), false, context);
			return;
		}
		rcb.onSuccess(new SingleFileStreamGenerator(data.asBucket(), persistent), data.getMetadata(), null, this, context);
	}

	@Override
	public void onSuccess(ClientKeyBlock block, boolean fromStore, Object reqTokenIgnored, ClientContext context) {
		if(parent instanceof ClientGetter)
			((ClientGetter)parent).addKeyToBinaryBlob(block, null, context);
		Bucket data = extract(block, context);
		if(data == null) return; // failed
		context.uskManager.checkUSK(key.getURI(), fromStore, null, block.isMetadata());
		if(!block.isMetadata()) {
			onSuccess(new FetchResult(new ClientMetadata(null), data), context);
		} else {
			onFailure(new FetchException(FetchException.INVALID_METADATA, "Metadata where expected data"), false, context);
		}
	}

	/** Convert a ClientKeyBlock to a Bucket. If an error occurs, report it via onFailure
	 * and return null.
	 */
	protected Bucket extract(ClientKeyBlock block, ClientContext context) {
		Bucket data;
		try {
			data = block.decode(context.getBucketFactory(parent.persistent()), (int)(Math.min(ctx.maxOutputLength, Integer.MAX_VALUE)), false);
		} catch (KeyDecodeException e1) {
			if(logMINOR)
				Logger.minor(this, "Decode failure: "+e1, e1);
			onFailure(new FetchException(FetchException.BLOCK_DECODE_ERROR, e1.getMessage()), false, context);
			return null;
		} catch (TooBigException e) {
			onFailure(new FetchException(FetchException.TOO_BIG, e), false, context);
			return null;
		} catch (InsufficientDiskSpaceException e) {
		    onFailure(new FetchException(FetchException.NOT_ENOUGH_DISK_SPACE), false, context);
		    return null;
		} catch (IOException e) {
			Logger.error(this, "Could not capture data - disk full?: "+e, e);
			onFailure(new FetchException(FetchException.BUCKET_ERROR, e), false, context);
			return null;
		}
		return data;
	}

	/** getToken() is not supported */
	@Override
	public long getToken() {
		return token;
	}

	@Override
	public void onFailed(KeyListenerConstructionException e, ClientContext context) {
		onFailure(e.getFetchException(), false, context);
	}

	@Override
	public void cancel(ClientContext context) {
		super.cancel(context);
		rcb.onFailure(new FetchException(FetchException.CANCELLED), this, context);
	}

	@Override
	protected void notFoundInStore(ClientContext context) {
		this.onFailure(new FetchException(FetchException.DATA_NOT_FOUND), true, context);
	}

	@Override
	protected void onBlockDecodeError(SendableRequestItem token, ClientContext context) {
		onFailure(new FetchException(FetchException.BLOCK_DECODE_ERROR, "Could not decode block with the URI given, probably invalid as inserted, possible the URI is wrong"), true, context);
	}

}
