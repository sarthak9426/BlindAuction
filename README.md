# OpenAuction

Tools Used - Truffle,Ganache,Solidity,Nodejs

Implementation of an open Auction using Smart contracts
A smart contract on Ethereum using the Truffle framework as a development environment. The Truffle Framework consists of three primary development frameworks for Ethereum smart contract and decentralized application (dApp) development called Truffle, Ganache, and Drizzle.  

Solidity, an object-oriented programming languagefor writing Ethereum’s smart contracts has been used to implement a smart contract for open auction. The general idea of the auction contract is that everyone can send their bids during a bidding period. The bids already  include sending money / ether in order to bind the bidders to their bid. If the highest bid is raised, the previously highest bidder gets their money back. After the end of the bidding period, the contract has to be called manually for the beneficiary to receive their money - contracts cannot activate themselves.
