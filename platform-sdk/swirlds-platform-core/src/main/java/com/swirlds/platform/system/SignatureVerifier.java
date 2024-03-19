package com.swirlds.platform.system;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Verifies signatures using the public keys of the nodes in the address book.
 */
public class SignatureVerifier {
    private static final Logger logger = LogManager.getLogger(SignatureVerifier.class);
    private static final SignatureType SIGNATURE_TYPE = SignatureType.RSA;
    private final Map<NodeId, Signature> signatureMap = new HashMap<>();

    /**
     * Create a new SignatureVerifier using the public keys of the nodes in the address book.
     *
     * @param addressBook
     * 		the address book containing the public keys of the nodes
     * @throws NoSuchAlgorithmException
     * 		if the algorithm used to create the signature is not available
     * @throws NoSuchProviderException
     * 		if the provider used to create the signature is not available
     * @throws InvalidKeyException
     * 		if the public key of a node is invalid
     */
    public SignatureVerifier(@NonNull final AddressBook addressBook)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException {
        for (final Address address : addressBook) {
            final String signingAlgorithm = SIGNATURE_TYPE.signingAlgorithm();
            final String sigProvider = SIGNATURE_TYPE.provider();
            final Signature sig = Signature.getInstance(signingAlgorithm, sigProvider);
            sig.initVerify(address.getSigPublicKey());
            signatureMap.put(address.getNodeId(), sig);
        }
    }

    /**
     * Verify the signature of the data using the public key of the node.
     *
     * @param nodeId
     * 		the ID of the node
     * @param data
     * 		the data to verify
     * @param signatureBytes
     * 		the signature to verify
     * @return true if the signature is valid, false otherwise
     */
    public boolean verifySignature(
            @NonNull final NodeId nodeId,
            @NonNull final byte[] data,
            @NonNull final byte[] signatureBytes) {
        Objects.requireNonNull(nodeId);
        Objects.requireNonNull(data);
        Objects.requireNonNull(signatureBytes);
        
        final Signature signature = signatureMap.get(nodeId);
        if (signature == null) {
            logger.error(EXCEPTION.getMarker(), "Cannot find node ID ({}) to verify signature", nodeId);
            return false;
        }
        try {
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (final SignatureException e) {
            logger.error(EXCEPTION.getMarker(), "Failed to verify signature", e);
            return false;
        }
    }
}
